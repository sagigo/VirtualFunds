-- ============================================================
-- Migration 003: Portfolio structural RPCs (E5.3, E5.4, E5.5)
-- All functions: SECURITY DEFINER, search_path = public
-- ============================================================

-- E5.3: Create a new active portfolio
CREATE OR REPLACE FUNCTION public.rpc_create_portfolio(
  p_name text,
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
  v_portfolio_id uuid;
  v_existing_portfolio_id uuid;
BEGIN
  -- Derive user_id from auth context
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- Normalize input name
  v_trimmed_name := trim(p_name);
  v_normalized_name := lower(v_trimmed_name);

  -- Validate not empty
  IF v_trimmed_name IS NULL OR v_trimmed_name = '' THEN
    RAISE EXCEPTION 'ERR_VALIDATION:EMPTY_NAME';
  END IF;

  -- Idempotency check: if summary row already exists, return existing portfolio_id
  SELECT portfolio_id INTO v_existing_portfolio_id
  FROM transactions
  WHERE transaction_id = p_summary_transaction_id;

  IF FOUND THEN
    RETURN v_existing_portfolio_id;
  END IF;

  -- Validate uniqueness among active portfolios for this user
  IF EXISTS (
    SELECT 1 FROM portfolios
    WHERE user_id = v_user_id
      AND normalized_name = v_normalized_name
      AND closed_at_utc IS NULL
  ) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:DUPLICATE_NAME';
  END IF;

  -- Insert portfolio
  v_portfolio_id := gen_random_uuid();
  INSERT INTO portfolios (portfolio_id, user_id, name, normalized_name)
  VALUES (v_portfolio_id, v_user_id, v_trimmed_name, v_normalized_name);

  -- Write history summary row
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, amount_agoras, summary_text
  ) VALUES (
    p_summary_transaction_id, v_user_id, v_portfolio_id, p_operation_id,
    'Summary', 'PortfolioCreated', 0,
    'Portfolio created: ' || v_trimmed_name
  );

  RETURN v_portfolio_id;
END;
$$;

-- E5.4: Rename an active portfolio
CREATE OR REPLACE FUNCTION public.rpc_rename_portfolio(
  p_portfolio_id uuid,
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
BEGIN
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- Idempotency check
  IF EXISTS (SELECT 1 FROM transactions WHERE transaction_id = p_summary_transaction_id) THEN
    RETURN;
  END IF;

  -- Lock portfolio row for update
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

  -- Normalize new name
  v_trimmed_name := trim(p_new_name);
  v_normalized_name := lower(v_trimmed_name);

  IF v_trimmed_name IS NULL OR v_trimmed_name = '' THEN
    RAISE EXCEPTION 'ERR_VALIDATION:EMPTY_NAME';
  END IF;

  -- Validate uniqueness among active portfolios (excluding self)
  IF EXISTS (
    SELECT 1 FROM portfolios
    WHERE user_id = v_user_id
      AND normalized_name = v_normalized_name
      AND closed_at_utc IS NULL
      AND portfolio_id <> p_portfolio_id
  ) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:DUPLICATE_NAME';
  END IF;

  -- Update portfolio
  UPDATE portfolios
  SET name = v_trimmed_name,
      normalized_name = v_normalized_name,
      updated_at_utc = now()
  WHERE portfolio_id = p_portfolio_id;

  -- Write history summary row
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, amount_agoras, summary_text
  ) VALUES (
    p_summary_transaction_id, v_user_id, p_portfolio_id, p_operation_id,
    'Summary', 'PortfolioRenamed', 0,
    'Portfolio renamed to: ' || v_trimmed_name
  );
END;
$$;

-- E5.5: Close (soft delete) a portfolio
CREATE OR REPLACE FUNCTION public.rpc_close_portfolio(
  p_portfolio_id uuid,
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
BEGIN
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- Idempotency check
  IF EXISTS (SELECT 1 FROM transactions WHERE transaction_id = p_summary_transaction_id) THEN
    RETURN;
  END IF;

  -- Lock portfolio row
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

  -- Close portfolio
  UPDATE portfolios
  SET closed_at_utc = now(),
      updated_at_utc = now()
  WHERE portfolio_id = p_portfolio_id;

  -- Disable all scheduled deposits in this portfolio
  UPDATE scheduled_deposits
  SET is_enabled = false,
      updated_at_utc = now()
  WHERE portfolio_id = p_portfolio_id
    AND is_enabled = true;

  -- Write history summary row
  INSERT INTO transactions (
    transaction_id, user_id, portfolio_id, operation_id,
    record_kind, transaction_type, amount_agoras, summary_text
  ) VALUES (
    p_summary_transaction_id, v_user_id, p_portfolio_id, p_operation_id,
    'Summary', 'PortfolioClosed', 0,
    'Portfolio closed: ' || v_portfolio.name
  );
END;
$$;
