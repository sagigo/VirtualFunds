"""
Sanity tests for fund structural RPCs:
  rpc_create_fund  (E5.6)
  rpc_rename_fund  (E5.7)
  rpc_delete_fund  (E5.8)
"""
import pytest
from uuid import uuid4
from conftest import new_op, unique, assert_error


class TestCreateFund:

    def test_returns_fund_id(self, client, portfolio_id):
        op_id, tx_id = new_op()
        result = client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": unique("Fund"),
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()
        assert isinstance(result.data, str)

    def test_fund_visible_with_zero_balance(self, client, portfolio_id):
        name = unique("Fund")
        op_id, tx_id = new_op()
        fund_id = client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": name,
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        row = (client.table("funds")
               .select("name, balance_agoras")
               .eq("fund_id", fund_id)
               .single()
               .execute())
        assert row.data["name"] == name
        assert row.data["balance_agoras"] == 0

    def test_initial_amount_sets_balance(self, client, portfolio_id):
        op_id, tx_id = new_op()
        fund_id = client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": unique("Fund"),
            "p_initial_amount_agoras": 5_000,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        row = (client.table("funds")
               .select("balance_agoras")
               .eq("fund_id", fund_id)
               .single()
               .execute())
        assert row.data["balance_agoras"] == 5_000

    def test_initial_amount_writes_deposit_detail(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": unique("Fund"),
            "p_initial_amount_agoras": 3_000,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        # Expect a Summary (FundCreated) and a Detail (FundDeposit)
        rows = (client.table("transactions")
                .select("record_kind, transaction_type")
                .eq("operation_id", op_id)
                .eq("portfolio_id", portfolio_id)
                .execute())
        types = {(r["record_kind"], r["transaction_type"]) for r in rows.data}
        assert ("Summary", "FundCreated") in types
        assert ("Detail", "FundDeposit") in types

    def test_empty_name_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_create_fund", {
                "p_portfolio_id": portfolio_id,
                "p_name": "",
                "p_initial_amount_agoras": 0,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:EMPTY_NAME")

    def test_duplicate_name_within_portfolio_raises(self, client, portfolio_id):
        name = unique("Fund")
        op_id, tx_id = new_op()
        client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": name,
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id2, tx_id2 = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_create_fund", {
                "p_portfolio_id": portfolio_id,
                "p_name": name,
                "p_initial_amount_agoras": 0,
                "p_operation_id": op_id2,
                "p_summary_transaction_id": tx_id2,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:DUPLICATE_NAME")

    def test_same_name_in_different_portfolio_is_ok(self, client):
        name = unique("Fund")

        op_id, tx_id = new_op()
        port1 = client.rpc("rpc_create_portfolio", {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        port2 = client.rpc("rpc_create_portfolio", {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        for port_id in [port1, port2]:
            op_id, tx_id = new_op()
            client.rpc("rpc_create_fund", {
                "p_portfolio_id": port_id,
                "p_name": name,
                "p_initial_amount_agoras": 0,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()  # both should succeed

    def test_negative_initial_amount_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_create_fund", {
                "p_portfolio_id": portfolio_id,
                "p_name": unique("Fund"),
                "p_initial_amount_agoras": -100,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:NEGATIVE_AMOUNT")

    def test_closed_portfolio_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_create_fund", {
                "p_portfolio_id": portfolio_id,
                "p_name": unique("Fund"),
                "p_initial_amount_agoras": 0,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:PORTFOLIO_CLOSED")

    def test_idempotent_returns_same_fund_id(self, client, portfolio_id):
        op_id, tx_id = new_op()
        params = {
            "p_portfolio_id": portfolio_id,
            "p_name": unique("Fund"),
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }
        id1 = client.rpc("rpc_create_fund", params).execute().data
        id2 = client.rpc("rpc_create_fund", params).execute().data
        assert id1 == id2


class TestRenameFund:

    def test_name_updated(self, client, portfolio_id, fund_id):
        new_name = unique("Renamed")
        op_id, tx_id = new_op()
        client.rpc("rpc_rename_fund", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_new_name": new_name,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        row = (client.table("funds")
               .select("name")
               .eq("fund_id", fund_id)
               .single()
               .execute())
        assert row.data["name"] == new_name

    def test_empty_name_raises(self, client, portfolio_id, fund_id):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_fund", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_id,
                "p_new_name": "",
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:EMPTY_NAME")

    def test_duplicate_name_raises(self, client, portfolio_id):
        # Create two funds; try to rename the second to the first's name
        op_id, tx_id = new_op()
        client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": "Alpha",
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id, tx_id = new_op()
        fund_b = client.rpc("rpc_create_fund", {
            "p_portfolio_id": portfolio_id,
            "p_name": "Beta",
            "p_initial_amount_agoras": 0,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_fund", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_b,
                "p_new_name": "Alpha",
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:DUPLICATE_NAME")

    def test_not_found_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_fund", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": str(uuid4()),
                "p_new_name": unique("Name"),
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")

    def test_idempotent(self, client, portfolio_id, fund_id):
        op_id, tx_id = new_op()
        params = {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_new_name": unique("Renamed"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }
        client.rpc("rpc_rename_fund", params).execute()
        client.rpc("rpc_rename_fund", params).execute()  # should not raise


class TestDeleteFund:

    def test_fund_removed(self, client, portfolio_id, fund_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_delete_fund", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        rows = (client.table("funds")
                .select("fund_id")
                .eq("fund_id", fund_id)
                .execute())
        assert rows.data == []

    def test_tombstone_written_to_deleted_funds(self, client, portfolio_id, fund_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_delete_fund", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        rows = (client.table("deleted_funds")
                .select("fund_id")
                .eq("fund_id", fund_id)
                .execute())
        assert len(rows.data) == 1

    def test_fund_with_balance_raises(self, client, funded_portfolio):
        port_id = funded_portfolio["portfolio_id"]
        f_id = funded_portfolio["fund_id"]

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_delete_fund", {
                "p_portfolio_id": port_id,
                "p_fund_id": f_id,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:FUND_NOT_EMPTY")

    def test_not_found_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_delete_fund", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": str(uuid4()),
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")

    def test_idempotent(self, client, portfolio_id, fund_id):
        op_id, tx_id = new_op()
        params = {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }
        client.rpc("rpc_delete_fund", params).execute()
        client.rpc("rpc_delete_fund", params).execute()  # should not raise
