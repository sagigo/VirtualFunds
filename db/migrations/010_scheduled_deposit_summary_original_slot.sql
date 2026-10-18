-- ============================================================
-- Migration 010: scheduled deposit summary text carries the
-- occurrence's original scheduled time (E8.9)
--
-- Catch-up executions (E8.6) all commit at trigger time, so their
-- history rows share one committed_at_utc and were indistinguishable
-- from one another. The summary text now embeds each occurrence's
-- original scheduled_for_utc (rendered in Israel time) so every
-- execution is traceable to the slot it was scheduled for.
--
-- The text also switches from English to Hebrew: both clients display
-- the stored summary text as-is (expanded history row, CSV export),
-- so the stored language is the display language.
-- New format: 'הפקדה מתוזמנת ״<name>״ — מועד מקורי: DD/MM/YYYY HH24:MI'
--             ("Scheduled deposit ״<name>״ — original slot: <timestamp>")
--
-- Replaces rpc_execute_due_scheduled_deposits from migration 008
-- (summary-text change only) and backfills existing history rows.
-- The __advance helper from 008 is unchanged and not redefined here.
-- ============================================================

CREATE OR REPLACE FUNCTION public.rpc_execute_due_scheduled_deposits(
  p_portfolio_id uuid,
  p_now_utc timestamptz,
  p_device_id uuid
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_portfolio record;
  v_sd scheduled_deposits;
  v_occ record;
  v_scheduled_for timestamptz;
  v_op_id uuid;
  v_summary_tx_id uuid;
  v_note_text text;
  v_results jsonb := '[]'::jsonb;
  v_executed_count int := 0;
BEGIN
  -- 1. Auth check
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- 2. Validate and lock portfolio
  SELECT * INTO v_portfolio
  FROM portfolios
  WHERE portfolio_id = p_portfolio_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'ERR_NOT_FOUND';
  END IF;

  IF v_portfolio.user_id <> v_user_id THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  IF v_portfolio.closed_at_utc IS NOT NULL THEN
    RAISE EXCEPTION 'ERR_VALIDATION:PORTFOLIO_CLOSED';
  END IF;

  -- 3. Process due scheduled deposits (E8.6: oldest first, cap at 30)
  -- Re-query each iteration to handle catch-up: after advancing a daily
  -- deposit by 1 day, it may still be overdue and gets picked up again.
  WHILE v_executed_count < 30 LOOP
    -- Find the next due deposit
    SELECT * INTO v_sd
    FROM scheduled_deposits
    WHERE portfolio_id = p_portfolio_id
      AND is_enabled = true
      AND next_run_at_utc <= p_now_utc
    ORDER BY next_run_at_utc ASC
    LIMIT 1;

    EXIT WHEN NOT FOUND;

    v_scheduled_for := v_sd.next_run_at_utc;

    -- 3a. Create occurrence row if missing (E8.5: unique constraint ensures exactly-once)
    INSERT INTO scheduled_deposit_occurrences (
      user_id, portfolio_id, scheduled_deposit_id, scheduled_for_utc, status
    ) VALUES (
      v_user_id, p_portfolio_id, v_sd.scheduled_deposit_id, v_scheduled_for, 'Pending'
    )
    ON CONFLICT (scheduled_deposit_id, scheduled_for_utc) DO NOTHING;

    -- Read the occurrence (whether just created or pre-existing)
    SELECT * INTO v_occ
    FROM scheduled_deposit_occurrences
    WHERE scheduled_deposit_id = v_sd.scheduled_deposit_id
      AND scheduled_for_utc = v_scheduled_for
    FOR UPDATE;

    -- 3b. If already done, advance next_run_at_utc to skip past it
    -- (edge case: previous execution completed but advancement failed)
    IF v_occ.status = 'Done' THEN
      PERFORM rpc_execute_due_scheduled_deposits__advance(
        v_sd, v_scheduled_for
      );
      CONTINUE;
    END IF;

    -- 3c. Attempt claim: Pending, or stale Claimed (> 10 min, E8.5 CLAIM_TTL)
    IF v_occ.status = 'Pending'
      OR (v_occ.status = 'Claimed'
          AND v_occ.claimed_at_utc < p_now_utc - INTERVAL '10 minutes') THEN

      UPDATE scheduled_deposit_occurrences
      SET status = 'Claimed',
          claimed_at_utc = p_now_utc,
          claimed_by_device_id = p_device_id
      WHERE occurrence_id = v_occ.occurrence_id
        AND (status = 'Pending'
             OR (status = 'Claimed'
                 AND claimed_at_utc < p_now_utc - INTERVAL '10 minutes'));

      IF NOT FOUND THEN
        -- Concurrent claim won (unreachable with portfolio lock, but be safe).
        -- Do not advance — let the claiming transaction handle it.
        EXIT;
      END IF;

      -- 3d. Execute the deposit via rpc_commit_fund_operation
      v_op_id := gen_random_uuid();
      v_summary_tx_id := gen_random_uuid();

      -- Build note: "SD:<id> <optional note text>"
      v_note_text := 'SD:' || v_sd.scheduled_deposit_id::text;
      IF v_sd.note IS NOT NULL AND v_sd.note <> '' THEN
        v_note_text := v_note_text || ' ' || v_sd.note;
      END IF;

      -- Summary text is displayed as-is by both clients (expanded history
      -- row + CSV export), hence Hebrew. It embeds the occurrence's original
      -- slot in Israel time so catch-up executions (E8.6) stay traceable:
      -- 'הפקדה מתוזמנת ״<name>״ — מועד מקורי: DD/MM/YYYY HH24:MI'
      PERFORM rpc_commit_fund_operation(
        p_portfolio_id := p_portfolio_id,
        p_operation_id := v_op_id,
        p_summary_transaction_id := v_summary_tx_id,
        p_summary_transaction_type := 'ScheduledDepositExecuted',
        p_summary_text := 'הפקדה מתוזמנת ״' || v_sd.name || '״ — מועד מקורי: '
                          || to_char(v_scheduled_for AT TIME ZONE 'Asia/Jerusalem',
                                     'DD/MM/YYYY HH24:MI'),
        p_note := v_note_text,
        p_created_by_device_id := p_device_id,
        p_details := jsonb_build_array(
          jsonb_build_object(
            'transaction_id', gen_random_uuid(),
            'fund_id', v_sd.fund_id,
            'amount_agoras', v_sd.amount_agoras,
            'transaction_type', 'FundDeposit'
          )
        )
      );

      -- Mark occurrence as Done
      UPDATE scheduled_deposit_occurrences
      SET status = 'Done',
          done_at_utc = p_now_utc,
          operation_id = v_op_id
      WHERE occurrence_id = v_occ.occurrence_id;

      -- Advance next_run_at_utc (E8.12)
      PERFORM rpc_execute_due_scheduled_deposits__advance(
        v_sd, v_scheduled_for
      );

      -- Add to results
      v_results := v_results || jsonb_build_object(
        'scheduled_deposit_id', v_sd.scheduled_deposit_id,
        'operation_id', v_op_id,
        'scheduled_for_utc', v_scheduled_for
      );

      v_executed_count := v_executed_count + 1;
    ELSE
      -- Claimed and not yet timed out (unreachable with portfolio lock).
      -- Do not advance — the claiming transaction will handle advancement.
      -- Exit the loop; a future trigger will pick up remaining work.
      EXIT;
    END IF;
  END LOOP;

  RETURN v_results;
END;
$$;

-- Security: CREATE OR REPLACE preserves the privileges set in migration 008,
-- but re-assert them so this migration stands on its own.
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits(uuid, timestamptz, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits(uuid, timestamptz, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_execute_due_scheduled_deposits(uuid, timestamptz, uuid) TO authenticated;

-- ============================================================
-- One-time backfill: rewrite existing ScheduledDepositExecuted summary
-- rows to the new format. The original slot is recovered from
-- scheduled_deposit_occurrences via operation_id. Uses the schedule's
-- current name (the name at execution time is not stored separately).
-- Display text only — no financial fields are touched.
--
-- Both UPDATEs are idempotent: the first matches by operation_id and
-- rewrites to the same value on re-run; the second only matches rows
-- still carrying the old English prefix. Safe to re-run if applied
-- statement-by-statement and interrupted midway.
-- ============================================================
UPDATE transactions t
SET summary_text = 'הפקדה מתוזמנת ״' || sd.name || '״ — מועד מקורי: '
                   || to_char(o.scheduled_for_utc AT TIME ZONE 'Asia/Jerusalem',
                              'DD/MM/YYYY HH24:MI')
FROM scheduled_deposit_occurrences o
JOIN scheduled_deposits sd ON sd.scheduled_deposit_id = o.scheduled_deposit_id
WHERE t.operation_id = o.operation_id
  AND t.record_kind = 'Summary'
  AND t.transaction_type = 'ScheduledDepositExecuted';

-- Occurrence rows are deleted together with their scheduled deposit
-- (E8.8), so rows whose schedule was since deleted have no recoverable
-- slot. Translate those to Hebrew, keeping the name embedded in the
-- old English text ('Scheduled deposit: <name>').
UPDATE transactions
SET summary_text = 'הפקדה מתוזמנת ״'
                   || substring(summary_text FROM char_length('Scheduled deposit: ') + 1)
                   || '״'
WHERE record_kind = 'Summary'
  AND transaction_type = 'ScheduledDepositExecuted'
  AND summary_text LIKE 'Scheduled deposit: %';
