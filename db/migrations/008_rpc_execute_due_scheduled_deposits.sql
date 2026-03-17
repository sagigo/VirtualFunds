-- ============================================================
-- Migration 008: rpc_execute_due_scheduled_deposits (E8.9)
-- Device-triggered, exactly-once execution of due scheduled deposits
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

      PERFORM rpc_commit_fund_operation(
        p_portfolio_id := p_portfolio_id,
        p_operation_id := v_op_id,
        p_summary_transaction_id := v_summary_tx_id,
        p_summary_transaction_type := 'ScheduledDepositExecuted',
        p_summary_text := 'Scheduled deposit: ' || v_sd.name,
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

-- ============================================================
-- Internal helper: advance next_run_at_utc per E8.12
-- Called after each occurrence is processed (executed or skipped).
-- ============================================================
CREATE OR REPLACE FUNCTION public.rpc_execute_due_scheduled_deposits__advance(
  p_sd scheduled_deposits,
  p_scheduled_for_utc timestamptz
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_next_run timestamptz;
  v_israel_date date;
  v_scheduled_time time;
  v_check_date date;
  v_dow int;
  v_found boolean;
  v_candidate timestamp;
BEGIN
  IF p_sd.schedule_kind = 'OneTime' THEN
    -- Do not advance; disable the deposit (E8.12)
    UPDATE scheduled_deposits
    SET is_enabled = false,
        updated_at_utc = now()
    WHERE scheduled_deposit_id = p_sd.scheduled_deposit_id;
    RETURN;
  END IF;

  -- Convert scheduled_for_utc to Israel date for advancement
  v_israel_date := (p_scheduled_for_utc AT TIME ZONE 'Asia/Jerusalem')::date;
  v_scheduled_time := make_time(
    p_sd.time_of_day_minutes / 60,
    p_sd.time_of_day_minutes % 60,
    0
  );

  CASE p_sd.schedule_kind
    WHEN 'Daily' THEN
      -- Advance by exactly one calendar day in Israel time (E8.12)
      v_next_run := ((v_israel_date + 1) + v_scheduled_time)
                    AT TIME ZONE 'Asia/Jerusalem';

    WHEN 'Weekly' THEN
      -- Find next weekday matching mask, strictly after current Israel date (E8.12)
      v_found := false;
      FOR i IN 1..7 LOOP
        v_check_date := v_israel_date + i;
        v_dow := EXTRACT(DOW FROM v_check_date)::int;
        IF (p_sd.weekday_mask & (1 << v_dow)) <> 0 THEN
          v_next_run := (v_check_date + v_scheduled_time)
                        AT TIME ZONE 'Asia/Jerusalem';
          v_found := true;
          EXIT;
        END IF;
      END LOOP;
      -- Safety fallback (should always match with a valid weekday_mask 1-127)
      IF NOT v_found THEN
        v_next_run := ((v_israel_date + 7) + v_scheduled_time)
                      AT TIME ZONE 'Asia/Jerusalem';
      END IF;

    WHEN 'Monthly' THEN
      -- Advance by one calendar month in Israel time (E8.12)
      -- Use year/month from one month ahead + original day_of_month.
      -- day_of_month <= 28 guarantees validity in all months.
      v_candidate := v_israel_date + INTERVAL '1 month';
      v_check_date := make_date(
        EXTRACT(YEAR FROM v_candidate)::int,
        EXTRACT(MONTH FROM v_candidate)::int,
        p_sd.day_of_month
      );
      v_next_run := (v_check_date + v_scheduled_time)
                    AT TIME ZONE 'Asia/Jerusalem';
  END CASE;

  UPDATE scheduled_deposits
  SET next_run_at_utc = v_next_run,
      updated_at_utc = now()
  WHERE scheduled_deposit_id = p_sd.scheduled_deposit_id;
END;
$$;

-- Security: only authenticated users can call the main RPC
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits(uuid, timestamptz, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits(uuid, timestamptz, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_execute_due_scheduled_deposits(uuid, timestamptz, uuid) TO authenticated;

-- The __advance helper is internal — no auth/ownership checks.
-- Revoke from all client-facing roles; only callable from SECURITY DEFINER functions.
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits__advance(scheduled_deposits, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits__advance(scheduled_deposits, timestamptz) FROM anon;
REVOKE ALL ON FUNCTION public.rpc_execute_due_scheduled_deposits__advance(scheduled_deposits, timestamptz) FROM authenticated;
