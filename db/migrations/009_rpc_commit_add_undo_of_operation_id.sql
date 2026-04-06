-- ============================================================
-- Migration 009: Add p_undo_of_operation_id to rpc_commit_fund_operation
-- Allows the summary row to record which operation was undone (E6.12).
-- Detail rows already read undo_of_operation_id from the JSONB payload.
-- ============================================================

-- Drop the old 10-parameter overload from migration 006 to avoid ambiguous
-- call resolution and stale permission grants.
DROP FUNCTION IF EXISTS public.rpc_commit_fund_operation(uuid, uuid, uuid, text, text, text, uuid, text, text, jsonb);

CREATE OR REPLACE FUNCTION public.rpc_commit_fund_operation(
  p_portfolio_id uuid,
  p_operation_id uuid,
  p_summary_transaction_id uuid,
  p_summary_transaction_type text,
  p_summary_text text,
  p_note text DEFAULT NULL,
  p_created_by_device_id uuid DEFAULT NULL,
  p_client_app text DEFAULT NULL,
  p_client_version text DEFAULT NULL,
  p_details jsonb DEFAULT '[]'::jsonb,
  p_undo_of_operation_id uuid DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_portfolio record;
  v_committed_at timestamptz;
  v_rec record;
  v_fund_id uuid;
  v_amount bigint;
  v_before_balance bigint;
  v_after_balance bigint;
  v_old_total bigint;
  v_expected_new_total bigint;
  v_actual_new_total bigint;
  v_delta_sum bigint := 0;
BEGIN
  -- 1. Derive user_id from auth context
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- 2. Idempotency check
  IF EXISTS (SELECT 1 FROM transactions WHERE transaction_id = p_summary_transaction_id) THEN
    RETURN;
  END IF;

  -- 3. Validate and lock portfolio
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

  -- Validate summary type is not structural
  IF p_summary_transaction_type IN (
    'PortfolioCreated', 'PortfolioRenamed', 'PortfolioClosed',
    'FundCreated', 'FundRenamed', 'FundDeleted'
  ) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:INVALID_TRANSACTION_TYPE';
  END IF;

  -- 4. Lock all funds in the portfolio (ordered by fund_id for deadlock prevention)
  PERFORM 1 FROM funds
  WHERE portfolio_id = p_portfolio_id
  ORDER BY fund_id
  FOR UPDATE;

  -- Compute old total for invariant check
  SELECT COALESCE(SUM(balance_agoras), 0) INTO v_old_total
  FROM funds
  WHERE portfolio_id = p_portfolio_id;

  -- Shared timestamp for all rows in this operation
  v_committed_at := now();

  -- 5-6. Process each detail: validate, apply fund update, insert detail row
  FOR v_rec IN SELECT * FROM jsonb_array_elements(p_details)
  LOOP
    v_fund_id := (v_rec.value->>'fund_id')::uuid;
    v_amount := (v_rec.value->>'amount_agoras')::bigint;

    -- Get current balance (funds already locked above)
    SELECT balance_agoras INTO v_before_balance
    FROM funds
    WHERE fund_id = v_fund_id
      AND portfolio_id = p_portfolio_id;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'ERR_NOT_FOUND';
    END IF;

    -- Compute new balance and reject if negative
    v_after_balance := v_before_balance + v_amount;

    IF v_after_balance < 0 THEN
      RAISE EXCEPTION 'ERR_INVARIANT:NEGATIVE_BALANCE';
    END IF;

    -- Apply fund update
    UPDATE funds
    SET balance_agoras = v_after_balance,
        updated_at_utc = v_committed_at
    WHERE fund_id = v_fund_id;

    -- Insert detail row with server-computed before/after balances
    INSERT INTO transactions (
      transaction_id, user_id, portfolio_id, operation_id,
      committed_at_utc, record_kind, transaction_type,
      fund_id, amount_agoras,
      before_balance_agoras, after_balance_agoras,
      undo_of_operation_id,
      created_by_device_id, client_app, client_version
    ) VALUES (
      (v_rec.value->>'transaction_id')::uuid,
      v_user_id, p_portfolio_id, p_operation_id,
      v_committed_at, 'Detail', v_rec.value->>'transaction_type',
      v_fund_id, v_amount,
      v_before_balance, v_after_balance,
      (v_rec.value->>'undo_of_operation_id')::uuid,
      p_created_by_device_id, p_client_app, p_client_version
    );

    v_delta_sum := v_delta_sum + v_amount;
  END LOOP;

  -- 7. Insert summary row (now includes undo_of_operation_id for undo operations)
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    committed_at_utc, record_kind, transaction_type,
    amount_agoras, summary_text, note,
    undo_of_operation_id,
    created_by_device_id, client_app, client_version
  ) VALUES (
    p_summary_transaction_id, v_user_id, p_portfolio_id, p_operation_id,
    v_committed_at, 'Summary', p_summary_transaction_type,
    v_delta_sum, p_summary_text, p_note,
    p_undo_of_operation_id,
    p_created_by_device_id, p_client_app, p_client_version
  );

  -- 8. Total invariant check (E6.7 step 8)
  SELECT COALESCE(SUM(balance_agoras), 0) INTO v_actual_new_total
  FROM funds
  WHERE portfolio_id = p_portfolio_id;

  v_expected_new_total := v_old_total + v_delta_sum;

  IF v_actual_new_total <> v_expected_new_total THEN
    RAISE EXCEPTION 'ERR_INVARIANT:TOTAL_MISMATCH';
  END IF;
END;
$$;

-- Security: only authenticated users can call this (must match new signature with 11 params)
REVOKE ALL ON FUNCTION public.rpc_commit_fund_operation(uuid, uuid, uuid, text, text, text, uuid, text, text, jsonb, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_commit_fund_operation(uuid, uuid, uuid, text, text, text, uuid, text, text, jsonb, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_commit_fund_operation(uuid, uuid, uuid, text, text, text, uuid, text, text, jsonb, uuid) TO authenticated;
