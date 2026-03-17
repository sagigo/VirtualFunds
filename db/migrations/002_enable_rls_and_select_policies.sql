-- ============================================================
-- Migration 002: Enable RLS and add SELECT policies (E4.4, E4.5)
--
-- E4.4: RLS on all user-owned tables, row belongs to user when user_id = auth.uid()
-- E4.5: Reads may be direct; writes must go through RPC (SECURITY DEFINER)
--       No INSERT/UPDATE/DELETE policies = direct writes blocked for clients.
-- ============================================================

-- portfolios
ALTER TABLE portfolios ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own portfolios"
  ON portfolios FOR SELECT
  USING (user_id = auth.uid());

-- funds
ALTER TABLE funds ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own funds"
  ON funds FOR SELECT
  USING (user_id = auth.uid());

-- deleted_funds
ALTER TABLE deleted_funds ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own deleted funds"
  ON deleted_funds FOR SELECT
  USING (user_id = auth.uid());

-- transactions
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own transactions"
  ON transactions FOR SELECT
  USING (user_id = auth.uid());

-- scheduled_deposits
ALTER TABLE scheduled_deposits ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own scheduled deposits"
  ON scheduled_deposits FOR SELECT
  USING (user_id = auth.uid());

-- scheduled_deposit_occurrences
ALTER TABLE scheduled_deposit_occurrences ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Users can view own scheduled deposit occurrences"
  ON scheduled_deposit_occurrences FOR SELECT
  USING (user_id = auth.uid());
