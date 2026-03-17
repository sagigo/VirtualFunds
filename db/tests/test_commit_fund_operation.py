"""
Sanity tests for rpc_commit_fund_operation (E6.7):
  deposit, withdrawal, transfer, undo, invariant checks.
"""
import pytest
from uuid import uuid4
from conftest import new_op, unique, assert_error


def deposit(client, portfolio_id, fund_id, amount: int):
    """Helper: deposit amount into fund. Returns (operation_id, summary_tx_id)."""
    op_id, tx_id = new_op()
    client.rpc("rpc_commit_fund_operation", {
        "p_portfolio_id": portfolio_id,
        "p_operation_id": op_id,
        "p_summary_transaction_id": tx_id,
        "p_summary_transaction_type": "FundDeposit",
        "p_summary_text": f"Deposit {amount}",
        "p_details": [{
            "transaction_id": str(uuid4()),
            "fund_id": fund_id,
            "amount_agoras": amount,
            "transaction_type": "FundDeposit",
        }],
    }).execute()
    return op_id, tx_id


def get_balance(client, fund_id) -> int:
    row = (client.table("funds")
           .select("balance_agoras")
           .eq("fund_id", fund_id)
           .single()
           .execute())
    return row.data["balance_agoras"]


class TestDeposit:

    def test_balance_increases(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        deposit(client, port_id, f_id, 1_000)
        assert get_balance(client, f_id) == 11_000

    def test_summary_and_detail_written(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        client.rpc("rpc_commit_fund_operation", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "FundDeposit",
            "p_summary_text": "Test deposit",
            "p_details": [{
                "transaction_id": str(uuid4()),
                "fund_id": f_id,
                "amount_agoras": 500,
                "transaction_type": "FundDeposit",
            }],
        }).execute()

        rows = (client.table("transactions")
                .select("record_kind, transaction_type, amount_agoras")
                .eq("operation_id", op_id)
                .execute())
        kinds = {(r["record_kind"], r["transaction_type"]) for r in rows.data}
        assert ("Summary", "FundDeposit") in kinds
        assert ("Detail", "FundDeposit") in kinds

    def test_detail_has_before_and_after_balance(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        client.rpc("rpc_commit_fund_operation", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "FundDeposit",
            "p_summary_text": "Test deposit",
            "p_details": [{
                "transaction_id": str(uuid4()),
                "fund_id": f_id,
                "amount_agoras": 200,
                "transaction_type": "FundDeposit",
            }],
        }).execute()

        rows = (client.table("transactions")
                .select("before_balance_agoras, after_balance_agoras")
                .eq("operation_id", op_id)
                .eq("record_kind", "Detail")
                .execute())
        assert rows.data[0]["before_balance_agoras"] == 10_000
        assert rows.data[0]["after_balance_agoras"] == 10_200

    def test_idempotent(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        detail_tx_id = str(uuid4())
        params = {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "FundDeposit",
            "p_summary_text": "Idempotent deposit",
            "p_details": [{
                "transaction_id": detail_tx_id,
                "fund_id": f_id,
                "amount_agoras": 100,
                "transaction_type": "FundDeposit",
            }],
        }
        client.rpc("rpc_commit_fund_operation", params).execute()
        client.rpc("rpc_commit_fund_operation", params).execute()  # second call is no-op

        # Balance should only increase by 100, not 200
        assert get_balance(client, f_id) == 10_100


class TestWithdrawal:

    def test_balance_decreases(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        client.rpc("rpc_commit_fund_operation", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "FundWithdrawal",
            "p_summary_text": "Withdrawal",
            "p_details": [{
                "transaction_id": str(uuid4()),
                "fund_id": f_id,
                "amount_agoras": -3_000,
                "transaction_type": "FundWithdrawal",
            }],
        }).execute()
        assert get_balance(client, f_id) == 7_000

    def test_negative_balance_raises(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_commit_fund_operation", {
                "p_portfolio_id": port_id,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
                "p_summary_transaction_type": "FundWithdrawal",
                "p_summary_text": "Overdraft",
                "p_details": [{
                    "transaction_id": str(uuid4()),
                    "fund_id": f_id,
                    "amount_agoras": -99_999,
                    "transaction_type": "FundWithdrawal",
                }],
            }).execute()
        assert_error(exc, "ERR_INVARIANT:NEGATIVE_BALANCE")


class TestTransfer:

    def test_net_delta_is_zero(self, client):
        # Set up two funds in one portfolio
        op_id, tx_id = new_op()
        port_id = client.rpc("rpc_create_portfolio", {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        fund_a = client.rpc("rpc_create_fund", {
            "p_portfolio_id": port_id,
            "p_name": "FundA",
            "p_initial_amount_agoras": 5_000,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        fund_b = client.rpc("rpc_create_fund", {
            "p_portfolio_id": port_id,
            "p_name": "FundB",
            "p_initial_amount_agoras": 3_000,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        # Transfer 2,000 from A to B
        op_id, tx_id = new_op()
        client.rpc("rpc_commit_fund_operation", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "Transfer",
            "p_summary_text": "Transfer A→B",
            "p_details": [
                {
                    "transaction_id": str(uuid4()),
                    "fund_id": fund_a,
                    "amount_agoras": -2_000,
                    "transaction_type": "TransferDebit",
                },
                {
                    "transaction_id": str(uuid4()),
                    "fund_id": fund_b,
                    "amount_agoras": 2_000,
                    "transaction_type": "TransferCredit",
                },
            ],
        }).execute()

        assert get_balance(client, fund_a) == 3_000
        assert get_balance(client, fund_b) == 5_000

    def test_summary_amount_is_zero(self, client):
        op_id, tx_id = new_op()
        port_id = client.rpc("rpc_create_portfolio", {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        fund_a = client.rpc("rpc_create_fund", {
            "p_portfolio_id": port_id, "p_name": "A",
            "p_initial_amount_agoras": 4_000,
            "p_operation_id": op_id, "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        fund_b = client.rpc("rpc_create_fund", {
            "p_portfolio_id": port_id, "p_name": "B",
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id, "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        client.rpc("rpc_commit_fund_operation", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "Transfer",
            "p_summary_text": "Transfer",
            "p_details": [
                {"transaction_id": str(uuid4()), "fund_id": fund_a,
                 "amount_agoras": -1_000, "transaction_type": "TransferDebit"},
                {"transaction_id": str(uuid4()), "fund_id": fund_b,
                 "amount_agoras": 1_000, "transaction_type": "TransferCredit"},
            ],
        }).execute()

        row = (client.table("transactions")
               .select("amount_agoras")
               .eq("transaction_id", tx_id)
               .single()
               .execute())
        assert row.data["amount_agoras"] == 0  # net delta is zero


class TestUndo:

    def test_undo_reverses_deposit(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        # Deposit 1,000
        original_op_id, _ = deposit(client, port_id, f_id, 1_000)
        assert get_balance(client, f_id) == 11_000

        # Undo it
        op_id, tx_id = new_op()
        client.rpc("rpc_commit_fund_operation", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
            "p_summary_transaction_type": "Undo",
            "p_summary_text": "Undo deposit",
            "p_details": [{
                "transaction_id": str(uuid4()),
                "fund_id": f_id,
                "amount_agoras": -1_000,
                "transaction_type": "Undo",
                "undo_of_operation_id": original_op_id,
            }],
        }).execute()

        assert get_balance(client, f_id) == 10_000


class TestValidation:

    def test_closed_portfolio_raises(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        # Close the portfolio
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": port_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_commit_fund_operation", {
                "p_portfolio_id": port_id,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
                "p_summary_transaction_type": "FundDeposit",
                "p_summary_text": "Should fail",
                "p_details": [{
                    "transaction_id": str(uuid4()),
                    "fund_id": f_id,
                    "amount_agoras": 100,
                    "transaction_type": "FundDeposit",
                }],
            }).execute()
        assert_error(exc, "ERR_VALIDATION:PORTFOLIO_CLOSED")

    def test_structural_type_raises(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_commit_fund_operation", {
                "p_portfolio_id": port_id,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
                "p_summary_transaction_type": "FundCreated",  # structural — blocked
                "p_summary_text": "Should fail",
                "p_details": [],
            }).execute()
        assert_error(exc, "ERR_VALIDATION:INVALID_TRANSACTION_TYPE")

    def test_fund_not_in_portfolio_raises(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_commit_fund_operation", {
                "p_portfolio_id": port_id,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
                "p_summary_transaction_type": "FundDeposit",
                "p_summary_text": "Bad fund",
                "p_details": [{
                    "transaction_id": str(uuid4()),
                    "fund_id": str(uuid4()),  # random, not in this portfolio
                    "amount_agoras": 100,
                    "transaction_type": "FundDeposit",
                }],
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")
