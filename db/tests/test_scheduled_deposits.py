"""
Sanity tests for scheduled deposit RPCs:
  rpc_upsert_scheduled_deposit          (E8.7)
  rpc_delete_scheduled_deposit          (E8.8)
  rpc_execute_due_scheduled_deposits    (E8.9)
"""
import pytest
from uuid import uuid4
from datetime import datetime, timezone, timedelta
from conftest import new_op, unique, assert_error


def now_utc_str() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def past_utc_str(hours: int = 2) -> str:
    return (datetime.now(timezone.utc) - timedelta(hours=hours)).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )


def get_balance(client, fund_id) -> int:
    row = (client.table("funds")
           .select("balance_agoras")
           .eq("fund_id", fund_id)
           .single()
           .execute())
    return row.data["balance_agoras"]


class TestUpsertScheduledDeposit:

    def test_create_daily_returns_id_and_next_run(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 1_000,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,  # 08:00
        }).execute()
        assert result.data["scheduled_deposit_id"] is not None
        assert result.data["next_run_at_utc"] is not None

    def test_create_weekly_returns_id_and_next_run(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 500,
            "p_schedule_kind": "Weekly",
            "p_time_of_day_minutes": 540,   # 09:00
            "p_weekday_mask": 10,           # Monday + Wednesday
        }).execute()
        assert result.data["scheduled_deposit_id"] is not None
        assert result.data["next_run_at_utc"] is not None

    def test_create_monthly_returns_id_and_next_run(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 2_000,
            "p_schedule_kind": "Monthly",
            "p_time_of_day_minutes": 600,  # 10:00
            "p_day_of_month": 15,
        }).execute()
        assert result.data["scheduled_deposit_id"] is not None
        assert result.data["next_run_at_utc"] is not None

    def test_create_onetime_stores_provided_time(self, client, portfolio_id, fund_id):
        run_at = past_utc_str(hours=1)  # 1 hour ago (valid for storage)
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 300,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": run_at,
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]
        assert sd_id is not None

        row = (client.table("scheduled_deposits")
               .select("schedule_kind, is_enabled")
               .eq("scheduled_deposit_id", sd_id)
               .single()
               .execute())
        assert row.data["schedule_kind"] == "OneTime"
        assert row.data["is_enabled"] is True

    def test_create_deposits_row_in_table(self, client, portfolio_id, fund_id):
        name = unique("SD")
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": name,
            "p_amount_agoras": 750,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]

        row = (client.table("scheduled_deposits")
               .select("name, amount_agoras, schedule_kind")
               .eq("scheduled_deposit_id", sd_id)
               .single()
               .execute())
        assert row.data["name"] == name
        assert row.data["amount_agoras"] == 750
        assert row.data["schedule_kind"] == "Daily"

    def test_update_existing_changes_name_and_amount(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]

        new_name = unique("Updated")
        client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": new_name,
            "p_amount_agoras": 999,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,
            "p_scheduled_deposit_id": sd_id,
        }).execute()

        row = (client.table("scheduled_deposits")
               .select("name, amount_agoras")
               .eq("scheduled_deposit_id", sd_id)
               .single()
               .execute())
        assert row.data["name"] == new_name
        assert row.data["amount_agoras"] == 999

    def test_disable_via_upsert(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,
            "p_is_enabled": True,
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]

        client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,
            "p_is_enabled": False,
            "p_scheduled_deposit_id": sd_id,
        }).execute()

        row = (client.table("scheduled_deposits")
               .select("is_enabled")
               .eq("scheduled_deposit_id", sd_id)
               .single()
               .execute())
        assert row.data["is_enabled"] is False

    def test_negative_amount_raises(self, client, portfolio_id, fund_id):
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_upsert_scheduled_deposit", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_id,
                "p_name": unique("SD"),
                "p_amount_agoras": -50,
                "p_schedule_kind": "Daily",
                "p_time_of_day_minutes": 480,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:NEGATIVE_AMOUNT")

    def test_daily_with_weekday_mask_raises(self, client, portfolio_id, fund_id):
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_upsert_scheduled_deposit", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_id,
                "p_name": unique("SD"),
                "p_amount_agoras": 100,
                "p_schedule_kind": "Daily",
                "p_time_of_day_minutes": 480,
                "p_weekday_mask": 10,  # not allowed for Daily
            }).execute()
        assert_error(exc, "ERR_VALIDATION:INVALID_SCHEDULE_FIELDS")

    def test_weekly_missing_weekday_mask_raises(self, client, portfolio_id, fund_id):
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_upsert_scheduled_deposit", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_id,
                "p_name": unique("SD"),
                "p_amount_agoras": 100,
                "p_schedule_kind": "Weekly",
                "p_time_of_day_minutes": 480,
                # p_weekday_mask omitted
            }).execute()
        assert_error(exc, "ERR_VALIDATION:INVALID_SCHEDULE_FIELDS")

    def test_onetime_missing_next_run_raises(self, client, portfolio_id, fund_id):
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_upsert_scheduled_deposit", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_id,
                "p_name": unique("SD"),
                "p_amount_agoras": 100,
                "p_schedule_kind": "OneTime",
                # p_next_run_at_utc omitted
            }).execute()
        assert_error(exc, "ERR_VALIDATION:INVALID_SCHEDULE_FIELDS")

    def test_fund_not_in_portfolio_raises(self, client, portfolio_id):
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_upsert_scheduled_deposit", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": str(uuid4()),  # random fund
                "p_name": unique("SD"),
                "p_amount_agoras": 100,
                "p_schedule_kind": "Daily",
                "p_time_of_day_minutes": 480,
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")

    def test_closed_portfolio_raises(self, client, portfolio_id, fund_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        with pytest.raises(Exception) as exc:
            client.rpc("rpc_upsert_scheduled_deposit", {
                "p_portfolio_id": portfolio_id,
                "p_fund_id": fund_id,
                "p_name": unique("SD"),
                "p_amount_agoras": 100,
                "p_schedule_kind": "Daily",
                "p_time_of_day_minutes": 480,
            }).execute()
        assert_error(exc, "ERR_VALIDATION:PORTFOLIO_CLOSED")


class TestDeleteScheduledDeposit:

    @pytest.fixture
    def scheduled_deposit_id(self, client, portfolio_id, fund_id) -> str:
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 480,
        }).execute()
        return result.data["scheduled_deposit_id"]

    def test_row_removed(self, client, scheduled_deposit_id):
        client.rpc("rpc_delete_scheduled_deposit", {
            "p_scheduled_deposit_id": scheduled_deposit_id,
        }).execute()

        rows = (client.table("scheduled_deposits")
                .select("scheduled_deposit_id")
                .eq("scheduled_deposit_id", scheduled_deposit_id)
                .execute())
        assert rows.data == []

    def test_not_found_raises(self, client):
        with pytest.raises(Exception) as exc:
            client.rpc("rpc_delete_scheduled_deposit", {
                "p_scheduled_deposit_id": str(uuid4()),
            }).execute()
        assert_error(exc, "ERR_NOT_FOUND")


class TestExecuteDueScheduledDeposits:

    def test_no_due_deposits_returns_empty(self, client, portfolio_id, fund_id):
        # Create a deposit scheduled for the future
        client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 500,
            "p_schedule_kind": "Daily",
            "p_time_of_day_minutes": 1,  # just after midnight — server computes next run
        }).execute()

        result = client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": "2000-01-01T00:00:00Z",  # far in the past — nothing is due
            "p_device_id": str(uuid4()),
        }).execute()

        assert result.data == []

    def test_due_onetime_deposit_executes(self, client, portfolio_id, fund_id):
        amount = 1_234

        # Create a OneTime deposit with next_run_at_utc in the past
        client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": amount,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": past_utc_str(hours=2),
        }).execute()

        result = client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now_utc_str(),
            "p_device_id": str(uuid4()),
        }).execute()

        executed = result.data
        assert len(executed) == 1
        assert executed[0]["scheduled_deposit_id"] is not None
        assert executed[0]["operation_id"] is not None

    def test_fund_balance_increased_after_execution(self, client, portfolio_id, fund_id):
        amount = 2_500

        client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": amount,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": past_utc_str(hours=1),
        }).execute()

        client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now_utc_str(),
            "p_device_id": str(uuid4()),
        }).execute()

        assert get_balance(client, fund_id) == amount

    def test_onetime_disabled_after_execution(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": past_utc_str(hours=1),
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]

        client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now_utc_str(),
            "p_device_id": str(uuid4()),
        }).execute()

        row = (client.table("scheduled_deposits")
               .select("is_enabled")
               .eq("scheduled_deposit_id", sd_id)
               .single()
               .execute())
        assert row.data["is_enabled"] is False

    def test_occurrence_marked_done(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": past_utc_str(hours=1),
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]

        client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now_utc_str(),
            "p_device_id": str(uuid4()),
        }).execute()

        rows = (client.table("scheduled_deposit_occurrences")
                .select("status")
                .eq("scheduled_deposit_id", sd_id)
                .execute())
        assert len(rows.data) == 1
        assert rows.data[0]["status"] == "Done"

    def test_transaction_written_with_correct_type(self, client, portfolio_id, fund_id):
        result = client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 100,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": past_utc_str(hours=1),
        }).execute()
        sd_id = result.data["scheduled_deposit_id"]

        executed = client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now_utc_str(),
            "p_device_id": str(uuid4()),
        }).execute().data

        op_id = executed[0]["operation_id"]
        rows = (client.table("transactions")
                .select("record_kind, transaction_type")
                .eq("operation_id", op_id)
                .execute())
        types = {(r["record_kind"], r["transaction_type"]) for r in rows.data}
        assert ("Summary", "ScheduledDepositExecuted") in types
        assert ("Detail", "FundDeposit") in types

    def test_idempotent_second_trigger_executes_nothing(self, client, portfolio_id, fund_id):
        client.rpc("rpc_upsert_scheduled_deposit", {
            "p_portfolio_id": portfolio_id,
            "p_fund_id": fund_id,
            "p_name": unique("SD"),
            "p_amount_agoras": 500,
            "p_schedule_kind": "OneTime",
            "p_next_run_at_utc": past_utc_str(hours=1),
        }).execute()

        device_id = str(uuid4())
        now = now_utc_str()

        result1 = client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now,
            "p_device_id": device_id,
        }).execute()
        result2 = client.rpc("rpc_execute_due_scheduled_deposits", {
            "p_portfolio_id": portfolio_id,
            "p_now_utc": now,
            "p_device_id": device_id,
        }).execute()

        assert len(result1.data) == 1
        assert len(result2.data) == 0  # already done — nothing executed
        assert get_balance(client, fund_id) == 500  # credited exactly once

    def test_closed_portfolio_raises(self, client, portfolio_id):
        op_id, tx_id = new_op()
        client.rpc("rpc_close_portfolio", {
            "p_portfolio_id": portfolio_id,
            "p_operation_id": op_id,
            "p_summary_transaction_id": tx_id,
        }).execute()

        with pytest.raises(Exception) as exc:
            client.rpc("rpc_execute_due_scheduled_deposits", {
                "p_portfolio_id": portfolio_id,
                "p_now_utc": now_utc_str(),
                "p_device_id": str(uuid4()),
            }).execute()
        assert_error(exc, "ERR_VALIDATION:PORTFOLIO_CLOSED")
