-- ============================================================
-- Migration 001: Create all base tables (E4.3)
-- ============================================================

-- E4.3.1: portfolios
CREATE TABLE portfolios (
  portfolio_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  name text NOT NULL,
  normalized_name text NOT NULL,
  created_at_utc timestamptz NOT NULL DEFAULT now(),
  updated_at_utc timestamptz NOT NULL DEFAULT now(),
  closed_at_utc timestamptz NULL
);

CREATE UNIQUE INDEX uix_portfolios_active_name
  ON portfolios (user_id, normalized_name)
  WHERE closed_at_utc IS NULL;

CREATE INDEX ix_portfolios_user_created
  ON portfolios (user_id, created_at_utc);

-- E4.3.2: funds
CREATE TABLE funds (
  fund_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  portfolio_id uuid NOT NULL REFERENCES portfolios(portfolio_id),
  name text NOT NULL,
  normalized_name text NOT NULL,
  balance_agoras bigint NOT NULL,
  created_at_utc timestamptz NOT NULL DEFAULT now(),
  updated_at_utc timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_funds_balance_non_negative CHECK (balance_agoras >= 0)
);

CREATE UNIQUE INDEX uix_funds_portfolio_name
  ON funds (portfolio_id, normalized_name);

CREATE INDEX ix_funds_portfolio_created
  ON funds (portfolio_id, created_at_utc);

-- E4.3.3: deleted_funds (tombstone for history name resolution)
CREATE TABLE deleted_funds (
  deleted_fund_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  portfolio_id uuid NOT NULL,
  fund_id uuid NOT NULL,
  name text NOT NULL,
  normalized_name text NOT NULL,
  deleted_at_utc timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uix_deleted_funds_portfolio_fund
  ON deleted_funds (portfolio_id, fund_id);

CREATE INDEX ix_deleted_funds_portfolio_deleted
  ON deleted_funds (portfolio_id, deleted_at_utc);

-- E4.3.4: transactions (append-only audit log)
CREATE TABLE transactions (
  transaction_id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  portfolio_id uuid NOT NULL,
  operation_id uuid NOT NULL,
  committed_at_utc timestamptz NOT NULL DEFAULT now(),
  record_kind text NOT NULL,
  transaction_type text NOT NULL,
  fund_id uuid NULL,
  amount_agoras bigint NOT NULL,
  before_balance_agoras bigint NULL,
  after_balance_agoras bigint NULL,
  undo_of_operation_id uuid NULL,
  summary_text text NULL,
  note text NULL,
  created_by_device_id uuid NULL,
  client_app text NULL,
  client_version text NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  CONSTRAINT chk_tx_record_kind CHECK (record_kind IN ('Summary', 'Detail')),
  CONSTRAINT chk_tx_summary_fund_null CHECK (
    record_kind <> 'Summary' OR fund_id IS NULL
  ),
  CONSTRAINT chk_tx_summary_text_not_null CHECK (
    record_kind <> 'Summary' OR summary_text IS NOT NULL
  ),
  CONSTRAINT chk_tx_detail_fund_not_null CHECK (
    record_kind <> 'Detail' OR fund_id IS NOT NULL
  )
);

CREATE INDEX ix_tx_portfolio_time
  ON transactions (portfolio_id, committed_at_utc DESC, operation_id, transaction_id);

CREATE INDEX ix_tx_portfolio_operation
  ON transactions (portfolio_id, operation_id);

CREATE INDEX ix_tx_portfolio_fund_time
  ON transactions (portfolio_id, fund_id, committed_at_utc DESC);

-- E4.3.5: scheduled_deposits
CREATE TABLE scheduled_deposits (
  scheduled_deposit_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  portfolio_id uuid NOT NULL REFERENCES portfolios(portfolio_id),
  name text NOT NULL,
  note text NULL,
  is_enabled boolean NOT NULL DEFAULT true,
  fund_id uuid NOT NULL,
  amount_agoras bigint NOT NULL,
  schedule_kind text NOT NULL,
  time_of_day_minutes int NULL,
  weekday_mask int NULL,
  day_of_month int NULL,
  next_run_at_utc timestamptz NOT NULL,
  created_at_utc timestamptz NOT NULL DEFAULT now(),
  updated_at_utc timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_sd_amount_positive CHECK (amount_agoras > 0),
  CONSTRAINT chk_sd_schedule_kind CHECK (schedule_kind IN ('Daily', 'Weekly', 'Monthly', 'OneTime')),
  CONSTRAINT chk_sd_time_of_day CHECK (
    time_of_day_minutes IS NULL OR time_of_day_minutes BETWEEN 0 AND 1439
  ),
  CONSTRAINT chk_sd_day_of_month CHECK (
    day_of_month IS NULL OR day_of_month BETWEEN 1 AND 28
  ),
  CONSTRAINT chk_sd_weekday_mask CHECK (
    weekday_mask IS NULL OR weekday_mask BETWEEN 1 AND 127
  )
);

CREATE INDEX ix_sd_portfolio_enabled_next
  ON scheduled_deposits (portfolio_id, is_enabled, next_run_at_utc);

CREATE INDEX ix_sd_portfolio_created
  ON scheduled_deposits (portfolio_id, created_at_utc);

-- E4.3.6: scheduled_deposit_occurrences (exactly-once coordination)
CREATE TABLE scheduled_deposit_occurrences (
  occurrence_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  portfolio_id uuid NOT NULL,
  scheduled_deposit_id uuid NOT NULL REFERENCES scheduled_deposits(scheduled_deposit_id),
  scheduled_for_utc timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'Pending',
  claimed_at_utc timestamptz NULL,
  claimed_by_device_id uuid NULL,
  done_at_utc timestamptz NULL,
  operation_id uuid NULL,
  created_at_utc timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT chk_sdo_status CHECK (status IN ('Pending', 'Claimed', 'Done'))
);

CREATE UNIQUE INDEX uix_sdo_deposit_scheduled
  ON scheduled_deposit_occurrences (scheduled_deposit_id, scheduled_for_utc);

CREATE INDEX ix_sdo_portfolio_status_scheduled
  ON scheduled_deposit_occurrences (portfolio_id, status, scheduled_for_utc);
