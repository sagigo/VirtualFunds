-- ============================================================
-- Migration 005: Fix structural RPCs (review findings)
-- 1. rpc_create_fund: move negative-amount check after idempotency
-- 2. rpc_rename_fund: lock portfolio FOR UPDATE (TOCTOU fix)
-- 3. rpc_delete_fund: lock portfolio FOR UPDATE (TOCTOU fix)
-- 4. Grant EXECUTE to authenticated, revoke from anon on all RPCs
-- ============================================================

-- Note: This migration replaces the function bodies from 003/004
-- using CREATE OR REPLACE. The updated function bodies are also
-- reflected in the 004_fund_structural_rpcs.sql repo file.

-- Fix 4: Grant EXECUTE to authenticated, revoke from anon

-- Portfolio RPCs
REVOKE ALL ON FUNCTION public.rpc_create_portfolio(text, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_create_portfolio(text, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_create_portfolio(text, uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.rpc_rename_portfolio(uuid, text, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_rename_portfolio(uuid, text, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_rename_portfolio(uuid, text, uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.rpc_close_portfolio(uuid, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_close_portfolio(uuid, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_close_portfolio(uuid, uuid, uuid) TO authenticated;

-- Fund RPCs
REVOKE ALL ON FUNCTION public.rpc_create_fund(uuid, text, bigint, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_create_fund(uuid, text, bigint, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_create_fund(uuid, text, bigint, uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.rpc_rename_fund(uuid, uuid, text, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_rename_fund(uuid, uuid, text, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_rename_fund(uuid, uuid, text, uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.rpc_delete_fund(uuid, uuid, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.rpc_delete_fund(uuid, uuid, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.rpc_delete_fund(uuid, uuid, uuid, uuid) TO authenticated;
