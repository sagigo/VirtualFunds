-- ============================================================
-- Migration 004: Fund structural RPCs (E5.6, E5.7, E5.8)
-- ============================================================

-- E5.6: Create a fund, optionally with an initial amount
CREATE OR REPLACE FUNCTION public.rpc_create_fund(
  p_portfolio_id uuid,
  p_name text,
  p_initial_amount_agoras bigint,
  p_operation_id uuid,
  p_summary_transaction_id uuid
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_trimmed_name text;
  v_normalized_name text;
  v_fund_id uuid;
  v_portfolio record;
  v_existing_fund_id uuid;
BEGIN
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- Idempotency check FIRST (before any validation)
  IF EXISTS (SELECT 1 FROM transactions WHERE transaction_id = p_summary_transaction_id) THEN
    SELECT fund_id INTO v_existing_fund_id
    FROM transactions
    WHERE operation_id = p_operation_id
      AND portfolio_id = p_portfolio_id
      AND record_kind = 'Detail'
      AND transaction_type = 'FundCreated'
    LIMIT 1;
    RETURN v_existing_fund_id;
  END IF;

  -- Validate initial amount (after idempotency check)
  IF p_initial_amount_agoras < 0 THEN
    RAISE EXCEPTION 'ERR_VALIDATION:NEGATIVE_AMOUNT';
  END IF;

  -- Lock portfolio
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

  -- Normalize name
  v_trimmed_name := trim(p_name);
  v_normalized_name := lower(v_trimmed_name);

  IF v_trimmed_name IS NULL OR v_trimmed_name = '' THEN
    RAISE EXCEPTION 'ERR_VALIDATION:EMPTY_NAME';
  END IF;

  -- Validate unique within portfolio
  IF EXISTS (
    SELECT 1 FROM funds
    WHERE portfolio_id = p_portfolio_id
      AND normalized_name = v_normalized_name
  ) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:DUPLICATE_NAME';
  END IF;

  -- Insert fund with balance = 0
  v_fund_id := gen_random_uuid();
  INSERT INTO funds (fund_id, user_id, portfolio_id, name, normalized_name, balance_agoras)
  VALUES (v_fund_id, v_user_id, p_portfolio_id, v_trimmed_name, v_normalized_name, 0);

  -- Write history: summary row
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, amount_agoras, summary_text
  ) VALUES (
    p_summary_transaction_id, v_user_id, p_portfolio_id, p_operation_id,
    'Summary', 'FundCreated', 0,
    CASE
      WHEN p_initial_amount_agoras = 0
        THEN 'Fund created: ' || v_trimmed_name
      ELSE 'Fund created: ' || v_trimmed_name || ' with initial balance ' || p_initial_amount_agoras
    END
  );

  -- Write history: detail row for FundCreated (structural, amount = 0)
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, fund_id, amount_agoras
  ) VALUES (
    gen_random_uuid(), v_user_id, p_portfolio_id, p_operation_id,
    'Detail', 'FundCreated', v_fund_id, 0
  );

  -- If initial amount > 0, deposit it
  IF p_initial_amount_agoras > 0 THEN
    UPDATE funds
    SET balance_agoras = p_initial_amount_agoras,
        updated_at_utc = now()
    WHERE fund_id = v_fund_id;

    INSERT INTO transactions (
      transaction_id, user_id, portfolio_id, operation_id,
      record_kind, transaction_type, fund_id, amount_agoras,
      before_balance_agoras, after_balance_agoras
    ) VALUES (
      gen_random_uuid(), v_user_id, p_portfolio_id, p_operation_id,
      'Detail', 'FundDeposit', v_fund_id, p_initial_amount_agoras,
      0, p_initial_amount_agoras
    );
  END IF;

  RETURN v_fund_id;
END;
$$;

-- E5.7: Rename a fund
CREATE OR REPLACE FUNCTION public.rpc_rename_fund(
  p_portfolio_id uuid,
  p_fund_id uuid,
  p_new_name text,
  p_operation_id uuid,
  p_summary_transaction_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_trimmed_name text;
  v_normalized_name text;
  v_portfolio record;
  v_fund record;
BEGIN
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- Idempotency check
  IF EXISTS (SELECT 1 FROM transactions WHERE transaction_id = p_summary_transaction_id) THEN
    RETURN;
  END IF;

  -- Lock portfolio FOR UPDATE (prevents TOCTOU with concurrent close)
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

  -- Lock fund row for update
  SELECT * INTO v_fund
  FROM funds
  WHERE fund_id = p_fund_id
    AND portfolio_id = p_portfolio_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'ERR_NOT_FOUND';
  END IF;

  -- Normalize new name
  v_trimmed_name := trim(p_new_name);
  v_normalized_name := lower(v_trimmed_name);

  IF v_trimmed_name IS NULL OR v_trimmed_name = '' THEN
    RAISE EXCEPTION 'ERR_VALIDATION:EMPTY_NAME';
  END IF;

  -- Validate unique within portfolio (excluding self)
  IF EXISTS (
    SELECT 1 FROM funds
    WHERE portfolio_id = p_portfolio_id
      AND normalized_name = v_normalized_name
      AND fund_id <> p_fund_id
  ) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:DUPLICATE_NAME';
  END IF;

  -- Update fund
  UPDATE funds
  SET name = v_trimmed_name,
      normalized_name = v_normalized_name,
      updated_at_utc = now()
  WHERE fund_id = p_fund_id;

  -- Write history: summary row
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, amount_agoras, summary_text
  ) VALUES (
    p_summary_transaction_id, v_user_id, p_portfolio_id, p_operation_id,
    'Summary', 'FundRenamed', 0,
    'Fund renamed to: ' || v_trimmed_name
  );

  -- Write history: detail row (structural, amount = 0)
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, fund_id, amount_agoras
  ) VALUES (
    gen_random_uuid(), v_user_id, p_portfolio_id, p_operation_id,
    'Detail', 'FundRenamed', p_fund_id, 0
  );
END;
$$;

-- E5.8: Delete a fund (hard delete + tombstone)
CREATE OR REPLACE FUNCTION public.rpc_delete_fund(
  p_portfolio_id uuid,
  p_fund_id uuid,
  p_operation_id uuid,
  p_summary_transaction_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_portfolio record;
  v_fund record;
BEGIN
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- Idempotency check
  IF EXISTS (SELECT 1 FROM transactions WHERE transaction_id = p_summary_transaction_id) THEN
    RETURN;
  END IF;

  -- Lock portfolio FOR UPDATE (prevents TOCTOU with concurrent close)
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

  -- Lock fund row for update
  SELECT * INTO v_fund
  FROM funds
  WHERE fund_id = p_fund_id
    AND portfolio_id = p_portfolio_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'ERR_NOT_FOUND';
  END IF;

  -- Validate balance is zero
  IF v_fund.balance_agoras <> 0 THEN
    RAISE EXCEPTION 'ERR_VALIDATION:FUND_NOT_EMPTY';
  END IF;

  -- Validate no enabled scheduled deposits target this fund
  IF EXISTS (
    SELECT 1 FROM scheduled_deposits
    WHERE fund_id = p_fund_id
      AND is_enabled = true
  ) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT';
  END IF;

  -- Insert tombstone into deleted_funds (for history name resolution)
  INSERT INTO deleted_funds (user_id, portfolio_id, fund_id, name, normalized_name)
  VALUES (v_user_id, p_portfolio_id, v_fund.fund_id, v_fund.name, v_fund.normalized_name);

  -- Delete the fund row
  DELETE FROM funds WHERE fund_id = p_fund_id;

  -- Write history: summary row
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, amount_agoras, summary_text
  ) VALUES (
    p_summary_transaction_id, v_user_id, p_portfolio_id, p_operation_id,
    'Summary', 'FundDeleted', 0,
    'Fund deleted: ' || v_fund.name
  );

  -- Write history: detail row (structural, amount = 0)
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, fund_id, amount_agoras
  ) VALUES (
    gen_random_uuid(), v_user_id, p_portfolio_id, p_operation_id,
    'Detail', 'FundDeleted', p_fund_id, 0
  );
END;
$$;
