"""
Sanity tests for portfolio structural RPCs:
  rpc_create_portfolio  (E5.3)
  rpc_rename_portfolio  (E5.4)
  rpc_close_portfolio   (E5.5)
"""
import pytest
from uuid import uuid4
from conftest import new_op, unique, assert_error


class TestCreatePortfolio:

    def test_returns_portfolio_id(self, client):
        op_id, tx_id = new_op()
        result = client.rpc("rpc_create_portfolio", {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()
        assert isinstance(result.data, str)

    def test_portfolio_visible_via_select(self, client):
        name = unique("Portfolio")
        op_id, tx_id = new_op()
        portfolio_id = client.rpc("rpc_create_portfolio", {
            "p_name": name,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        row = (client.table("portfolios")
               .select("name, closed_at_utc")
               .eq("portfolio_id", portfolio_id)
               .single()
               .execute())
        assert row.data["name"] == name
        assert row.data["closed_at_utc"] is None

    def test_summary_transaction_written(self, client):
        op_id, tx_id = new_op()
        portfolio_id = client.rpc("rpc_create_portfolio", {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        rows = (client.table("transactions")
                .select("record_kind, transaction_type")
                .eq("transaction_id", tx_id)
                .execute())
        assert len(rows.data) == 1
        assert rows.data[0]["record_kind"] == "Summary"
        assert rows.data[0]["transaction_type"] == "PortfolioCreated"

    def test_empty_name_raises(self, client):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_create_portfolio", {
                "p_name": "   ",
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:EMPTY_NAME")

    def test_duplicate_active_name_raises(self, client):
        name = unique("Portfolio")
        op_id, tx_id = new_op()
        client.rpc("rpc_create_portfolio", {
            "p_name": name,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id2, tx_id2 = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_create_portfolio", {
                "p_name": name,
                "p_operation_id": op_id2,
                "p_summary_transaction_id": tx_id2,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:DUPLICATE_NAME")

    def test_idempotent_returns_same_id(self, client):
        op_id, tx_id = new_op()
        params = {
            "p_name": unique("Portfolio"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }
        id1 = client.rpc("rpc_create_portfolio", params).execute().data
        id2 = client.rpc("rpc_create_portfolio", params).execute().data
        assert id1 == id2

    def test_name_is_trimmed(self, client):
        name = unique("Portfolio")
        op_id, tx_id = new_op()
        portfolio_id = client.rpc("rpc_create_portfolio", {
            "p_name": f"  {name}  ",
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        row = (client.table("portfolios")
               .select("name")
               .eq("portfolio_id", portfolio_id)
               .single()
               .execute())
        assert row.data["name"] == name


class TestRenamePortfolio:

    def test_name_updated(self, client, portfolio_id):
        new_name = unique("Renamed")
        op_id, tx_id = new_op()
        client.rpc("rpc_rename_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_new_name": new_name,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        row = (client.table("portfolios")
               .select("name")
               .eq("portfolio_id", portfolio_id)
               .single()
               .execute())
        assert row.data["name"] == new_name

    def test_summary_transaction_written(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_rename_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_new_name": unique("Renamed"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        rows = (client.table("transactions")
                .select("transaction_type")
                .eq("transaction_id", tx_id)
                .execute())
        assert rows.data[0]["transaction_type"] == "PortfolioRenamed"

    def test_empty_name_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_portfolio", {
                "p_portfolio_id": portfolio_id,
                "p_new_name": "",
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:EMPTY_NAME")

    def test_duplicate_name_raises(self, client):
        # Two portfolios with same name — rename second to first's name
        name_a = unique("Portfolio")
        name_b = unique("Portfolio")
        op_id, tx_id = new_op()
        client.rpc("rpc_create_portfolio", {
            "p_name": name_a,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id, tx_id = new_op()
        port_b = client.rpc("rpc_create_portfolio", {
            "p_name": name_b,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_portfolio", {
                "p_portfolio_id": port_b,
                "p_new_name": name_a,
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:DUPLICATE_NAME")

    def test_not_found_raises(self, client):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_portfolio", {
                "p_portfolio_id": str(uuid4()),
                "p_new_name": unique("Name"),
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")

    def test_closed_portfolio_raises(self, client, portfolio_id):
        # Close it first
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_rename_portfolio", {
                "p_portfolio_id": portfolio_id,
                "p_new_name": unique("Name"),
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:PORTFOLIO_CLOSED")

    def test_idempotent(self, client, portfolio_id):
        op_id, tx_id = new_op()
        params = {
            "p_portfolio_id": portfolio_id,
            "p_new_name": unique("Renamed"),
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }
        client.rpc("rpc_rename_portfolio", params).execute()
        client.rpc("rpc_rename_portfolio", params).execute()  # should not raise


class TestClosePortfolio:

    def test_closed_at_utc_is_set(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        row = (client.table("portfolios")
               .select("closed_at_utc")
               .eq("portfolio_id", portfolio_id)
               .single()
               .execute())
        assert row.data["closed_at_utc"] is not None

    def test_summary_transaction_written(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        rows = (client.table("transactions")
                .select("transaction_type")
                .eq("transaction_id", tx_id)
                .execute())
        assert rows.data[0]["transaction_type"] == "PortfolioClosed"

    def test_already_closed_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        op_id2, tx_id2 = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_close_portfolio", {
                "p_portfolio_id": portfolio_id,
                "p_operation_id": op_id2,
                "p_summary_transaction_id": tx_id2,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:PORTFOLIO_CLOSED")

    def test_not_found_raises(self, client):
        op_id, tx_id = new_op()
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_close_portfolio", {
                "p_portfolio_id": str(uuid4()),
                "p_operation_id": op_id,
                "p_summary_transaction_id": tx_id,
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")

    def test_closed_name_reusable_in_new_portfolio(self, client, portfolio_id):
        # Get the original name
        row = (client.table("portfolios")
               .select("name")
               .eq("portfolio_id", portfolio_id)
               .single()
               .execute())
        name = row.data["name"]

        # Close it
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        # Re-use the same name for a new portfolio — should succeed
        op_id, tx_id = new_op()
        new_id = client.rpc("rpc_create_portfolio", {
            "p_name": name,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute().data
        assert new_id is not None
        assert new_id != portfolio_id

    def test_idempotent(self, client, portfolio_id):
        op_id, tx_id = new_op()
        params = {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }
        client.rpc("rpc_close_portfolio", params).execute()
        client.rpc("rpc_close_portfolio", params).execute()  # should not raise
