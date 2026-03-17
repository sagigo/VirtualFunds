import os
import pytest
from uuid import uuid4
from dotenv import load_dotenv
from supabase import create_client, Client

load_dotenv()

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_ANON_KEY = os.environ["SUPABASE_ANON_KEY"]
TEST_USER_EMAIL = os.environ["TEST_USER_EMAIL"]
TEST_USER_PASSWORD = os.environ["TEST_USER_PASSWORD"]


# ---------------------------------------------------------------------------
# Helpers (plain functions, not fixtures)
# ---------------------------------------------------------------------------

def new_op() -> tuple[str, str]:
    """Return a fresh (operation_id, summary_transaction_id) pair."""
    return str(uuid4()), str(uuid4())


def unique(prefix: str = "Test") -> str:
    """Return a unique name that won't collide across test runs."""
    return f"{prefix}_{uuid4().hex[:8]}"


def assert_error(exc_info, token: str):
    """Assert the exception message contains the expected error token."""
    assert token in str(exc_info.value), (
        f"Expected '{token}' in error, got: {exc_info.value}"
    )


# ---------------------------------------------------------------------------
# Session-scoped client (sign in once for the entire test run)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def client() -> Client:
    sb = create_client(SUPABASE_URL, SUPABASE_ANON_KEY)
    sb.auth.sign_in_with_password({
        "email": TEST_USER_EMAIL,
        "password": TEST_USER_PASSWORD,
    })
    return sb


# ---------------------------------------------------------------------------
# Common fixtures (function-scoped: fresh data per test)
# ---------------------------------------------------------------------------

@pytest.fixture
def portfolio_id(client) -> str:
    """Creates a fresh active portfolio and returns its UUID."""
    op_id, tx_id = new_op()
    return client.rpc("rpc_create_portfolio", {
        "p_name": unique("Portfolio"),
        "p_operation_id": op_id,
        "p_summary_transaction_id": tx_id,
    }).execute().data


@pytest.fixture
def fund_id(client, portfolio_id) -> str:
    """Creates a zero-balance fund in the portfolio and returns its UUID."""
    op_id, tx_id = new_op()
    return client.rpc("rpc_create_fund", {
        "p_portfolio_id": portfolio_id,
        "p_name": unique("Fund"),
        "p_initial_amount_agoras": 0,
        "p_operation_id": op_id,
        "p_summary_transaction_id": tx_id,
    }).execute().data


@pytest.fixture
def funded_portfolio(client) -> dict:
    """Creates a portfolio with a fund pre-loaded with 10,000 agoras.

    Returns {"portfolio_id": ..., "fund_id": ...}.
    """
    op_id, tx_id = new_op()
    port_id = client.rpc("rpc_create_portfolio", {
        "p_name": unique("Portfolio"),
        "p_operation_id": op_id,
        "p_summary_transaction_id": tx_id,
    }).execute().data

    op_id, tx_id = new_op()
    f_id = client.rpc("rpc_create_fund", {
        "p_portfolio_id": port_id,
        "p_name": unique("Fund"),
        "p_initial_amount_agoras": 10_000,
        "p_operation_id": op_id,
        "p_summary_transaction_id": tx_id,
    }).execute().data

    return {"portfolio_id": port_id, "fund_id": f_id}
