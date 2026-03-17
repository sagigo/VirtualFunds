-- ============================================================
-- Migration 007: Scheduled deposit CRUD RPCs (E8.7, E8.8)
-- ============================================================

-- E8.7: Create or update a scheduled deposit
-- Also used to enable/disable (pass p_is_enabled = true/false)
CREATE OR REPLACE FUNCTION public.rpc_upsert_scheduled_deposit(
  p_portfolio_id uuid,
  p_fund_id uuid,
  p_name text,
  p_amount_agoras bigint,
  p_schedule_kind text,
  p_is_enabled boolean DEFAULT true,
  p_note text DEFAULT NULL,
  p_time_of_day_minutes int DEFAULT NULL,
  p_weekday_mask int DEFAULT NULL,
  p_day_of_month int DEFAULT NULL,
  p_next_run_at_utc timestamptz DEFAULT NULL,
  p_scheduled_deposit_id uuid DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_portfolio record;
  v_trimmed_name text;
  v_computed_next_run timestamptz;
  v_sd_id uuid;
  v_israel_now timestamp;
  v_today date;
  v_scheduled_time time;
  v_candidate timestamp;
  v_check_date date;
  v_dow int;
  v_found boolean;
BEGIN
  -- 1. Auth check
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- 2. Lock and validate portfolio
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

  -- 3. Validate target fund exists in same portfolio
  IF NOT EXISTS (
    SELECT 1 FROM funds
    WHERE fund_id = p_fund_id
      AND portfolio_id = p_portfolio_id
  ) THEN
    RAISE EXCEPTION 'ERR_NOT_FOUND';
  END IF;

  -- 4. Validate name
  v_trimmed_name := trim(p_name);
  IF v_trimmed_name IS NULL OR v_trimmed_name = '' THEN
    RAISE EXCEPTION 'ERR_VALIDATION:EMPTY_NAME';
  END IF;

  -- 5. Validate amount
  IF p_amount_agoras <= 0 THEN
    RAISE EXCEPTION 'ERR_VALIDATION:NEGATIVE_AMOUNT';
  END IF;

  -- 6. Validate schedule kind
  IF p_schedule_kind NOT IN ('OneTime', 'Daily', 'Weekly', 'Monthly') THEN
    RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_KIND';
  END IF;

  -- 7. Validate schedule-specific fields (E8.2)
  CASE p_schedule_kind
    WHEN 'OneTime' THEN
      IF p_next_run_at_utc IS NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;
      IF p_time_of_day_minutes IS NOT NULL
        OR p_weekday_mask IS NOT NULL
        OR p_day_of_month IS NOT NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;

    WHEN 'Daily' THEN
      IF p_time_of_day_minutes IS NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;
      IF p_weekday_mask IS NOT NULL OR p_day_of_month IS NOT NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;

    WHEN 'Weekly' THEN
      IF p_time_of_day_minutes IS NULL OR p_weekday_mask IS NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;
      IF p_day_of_month IS NOT NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;

    WHEN 'Monthly' THEN
      IF p_time_of_day_minutes IS NULL OR p_day_of_month IS NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;
      IF p_weekday_mask IS NOT NULL THEN
        RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
      END IF;
  END CASE;

  -- Range checks
  IF p_time_of_day_minutes IS NOT NULL
    AND (p_time_of_day_minutes < 0 OR p_time_of_day_minutes > 1439) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
  END IF;

  IF p_weekday_mask IS NOT NULL
    AND (p_weekday_mask < 1 OR p_weekday_mask > 127) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
  END IF;

  IF p_day_of_month IS NOT NULL
    AND (p_day_of_month < 1 OR p_day_of_month > 28) THEN
    RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
  END IF;

  -- 8. Compute next_run_at_utc (E8.12)
  IF p_schedule_kind = 'OneTime' THEN
    -- Client provides the single execution time directly
    v_computed_next_run := p_next_run_at_utc;
  ELSE
    -- Compute initial next_run_at_utc in Israel time, then convert to UTC
    v_israel_now := now() AT TIME ZONE 'Asia/Jerusalem';
    v_today := v_israel_now::date;
    v_scheduled_time := make_time(
      p_time_of_day_minutes / 60,
      p_time_of_day_minutes % 60,
      0
    );

    CASE p_schedule_kind
      WHEN 'Daily' THEN
        -- If today at scheduled time is still in the future, use today;
        -- otherwise use tomorrow
        v_candidate := v_today + v_scheduled_time;
        IF v_candidate > v_israel_now THEN
          v_computed_next_run := v_candidate AT TIME ZONE 'Asia/Jerusalem';
        ELSE
          v_computed_next_run := (v_today + 1 + v_scheduled_time)
                                AT TIME ZONE 'Asia/Jerusalem';
        END IF;

      WHEN 'Weekly' THEN
        -- Find the next matching weekday (today through today+6)
        -- that is strictly in the future
        v_found := false;
        FOR i IN 0..6 LOOP
          v_check_date := v_today + i;
          -- EXTRACT(DOW) returns 0=Sunday..6=Saturday, matching weekday_mask encoding
          v_dow := EXTRACT(DOW FROM v_check_date)::int;
          IF (p_weekday_mask & (1 << v_dow)) <> 0 THEN
            v_candidate := v_check_date + v_scheduled_time;
            IF v_candidate > v_israel_now THEN
              v_computed_next_run := v_candidate AT TIME ZONE 'Asia/Jerusalem';
              v_found := true;
              EXIT;
            END IF;
          END IF;
        END LOOP;
        -- Safety: if today's matching slot just passed, check next 7 days
        -- (with valid weekday_mask 1-127, at least one bit is set, so this
        -- always finds a match within 7 days)
        IF NOT v_found THEN
          FOR i IN 7..13 LOOP
            v_check_date := v_today + i;
            v_dow := EXTRACT(DOW FROM v_check_date)::int;
            IF (p_weekday_mask & (1 << v_dow)) <> 0 THEN
              v_computed_next_run := (v_check_date + v_scheduled_time)
                                    AT TIME ZONE 'Asia/Jerusalem';
              v_found := true;
              EXIT;
            END IF;
          END LOOP;
        END IF;
        -- Defensive: should never happen with valid mask, but guard against null
        IF NOT v_found THEN
          RAISE EXCEPTION 'ERR_VALIDATION:INVALID_SCHEDULE_FIELDS';
        END IF;

      WHEN 'Monthly' THEN
        -- Try this month's day_of_month; if past, use next month.
        -- day_of_month is capped at 28, so always valid.
        v_check_date := make_date(
          EXTRACT(YEAR FROM v_today)::int,
          EXTRACT(MONTH FROM v_today)::int,
          p_day_of_month
        );
        v_candidate := v_check_date + v_scheduled_time;
        IF v_candidate > v_israel_now THEN
          v_computed_next_run := v_candidate AT TIME ZONE 'Asia/Jerusalem';
        ELSE
          v_check_date := (v_check_date + INTERVAL '1 month')::date;
          v_computed_next_run := (v_check_date + v_scheduled_time)
                                AT TIME ZONE 'Asia/Jerusalem';
        END IF;
    END CASE;
  END IF;

  -- 9. Insert or update
  IF p_scheduled_deposit_id IS NULL THEN
    -- Create new scheduled deposit
    v_sd_id := gen_random_uuid();
    INSERT INTO scheduled_deposits (
      scheduled_deposit_id, user_id, portfolio_id, fund_id,
      name, note, is_enabled, amount_agoras,
      schedule_kind, time_of_day_minutes, weekday_mask, day_of_month,
      next_run_at_utc
    ) VALUES (
      v_sd_id, v_user_id, p_portfolio_id, p_fund_id,
      v_trimmed_name, p_note, p_is_enabled, p_amount_agoras,
      p_schedule_kind, p_time_of_day_minutes, p_weekday_mask, p_day_of_month,
      v_computed_next_run
    );
  ELSE
    -- Update existing: lock row and validate ownership
    PERFORM 1 FROM scheduled_deposits
    WHERE scheduled_deposit_id = p_scheduled_deposit_id
      AND portfolio_id = p_portfolio_id
      AND user_id = v_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'ERR_NOT_FOUND';
    END IF;

    v_sd_id := p_scheduled_deposit_id;
    UPDATE scheduled_deposits
    SET fund_id = p_fund_id,
        name = v_trimmed_name,
        note = p_note,
        is_enabled = p_is_enabled,
        amount_agoras = p_amount_agoras,
        schedule_kind = p_schedule_kind,
        time_of_day_minutes = p_time_of_day_minutes,
        weekday_mask = p_weekday_mask,
        day_of_month = p_day_of_month,
        next_run_at_utc = v_computed_next_run,
        updated_at_utc = now()
    WHERE scheduled_deposit_id = p_scheduled_deposit_id;
  END IF;

  -- 10. Return identifier and computed next run time
  RETURN jsonb_build_object(
    'scheduled_deposit_id', v_sd_id,
    'next_run_at_utc', v_computed_next_run
  );
END;
$$;

-- E8.8: Delete a scheduled deposit
CREATE OR REPLACE FUNCTION public.rpc_delete_scheduled_deposit(
  p_scheduled_deposit_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_user_id uuid;
  v_sd record;
  v_portfolio record;
BEGIN
  -- 1. Auth check
  v_user_id := auth.uid();
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- 2. Look up and lock the scheduled deposit
  SELECT * INTO v_sd
  FROM scheduled_deposits
  WHERE scheduled_deposit_id = p_scheduled_deposit_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'ERR_NOT_FOUND';
  END IF;

  IF v_sd.user_id <> v_user_id THEN
    RAISE EXCEPTION 'ERR_UNAUTHORIZED';
  END IF;

  -- 3. Validate portfolio is active
  SELECT * INTO v_portfolio
  FROM portfolios
  WHERE portfolio_id = v_sd.portfolio_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'ERR_NOT_FOUND';
  END IF;

  IF v_portfolio.closed_at_utc IS NOT NULL THEN
    RAISE EXCEPTION 'ERR_VALIDATION:PORTFOLIO_CLOSED';
  END IF;

  -- 4. Delete occurrence rows first (FK constraint prevents deleting parent)
  -- Occurrence rows are coordination state; the real audit trail is in transactions.
  DELETE FROM scheduled_deposit_occurrences
  WHERE scheduled_deposit_id = p_scheduled_deposit_id;

  -- 5. Delete the scheduled deposit
  DELETE FROM scheduled_deposits
  WHERE scheduled_deposit_id = p_scheduled_deposit_id;
END;
$$;

-- Security: only authenticated users can call these RPCs
REVOKE ALL ON FUNCTION public.rpc_upsert_scheduled_deposit(uuid, uuid, text, bigint, text, boolean, text, int, int, int, timestamptz, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_upsert_scheduled_deposit(uuid, uuid, text, bigint, text, boolean, text, int, int, int, timestamptz, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_upsert_scheduled_deposit(uuid, uuid, text, bigint, text, boolean, text, int, int, int, timestamptz, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.rpc_delete_scheduled_deposit(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_delete_scheduled_deposit(uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_delete_scheduled_deposit(uuid) TO authenticated;
