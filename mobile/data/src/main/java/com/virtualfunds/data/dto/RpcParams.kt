package com.virtualfunds.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `@Serializable` parameter shapes for every RPC the mobile app calls.
 *
 * Each class mirrors the `p_*` parameter list of a PL/pgSQL function in
 * `db/migrations/` 1:1. The field-level `@SerialName("p_*")` keys are required —
 * Postgrest sends the JSON body straight into the function's named parameters.
 *
 * We use these typed parameter classes (rather than building a [kotlinx.serialization.json.JsonObject]
 * by hand) so the compiler catches missing or misnamed fields at the call site, matching the
 * desktop's anonymous-object pattern but with checked types.
 */

// ── Portfolio structural RPCs (E5.3–E5.5) ───────────────────────────────────

/** Parameters for `rpc_create_portfolio` (E5.3). */
@Serializable
data class CreatePortfolioParams(
    @SerialName("p_name")                   val name: String,
    @SerialName("p_operation_id")           val operationId: String,
    @SerialName("p_summary_transaction_id") val summaryTransactionId: String,
)

/** Parameters for `rpc_rename_portfolio` (E5.4). */
@Serializable
data class RenamePortfolioParams(
    @SerialName("p_portfolio_id")           val portfolioId: String,
    @SerialName("p_new_name")               val newName: String,
    @SerialName("p_operation_id")           val operationId: String,
    @SerialName("p_summary_transaction_id") val summaryTransactionId: String,
)

/** Parameters for `rpc_close_portfolio` (E5.5). */
@Serializable
data class ClosePortfolioParams(
    @SerialName("p_portfolio_id")           val portfolioId: String,
    @SerialName("p_operation_id")           val operationId: String,
    @SerialName("p_summary_transaction_id") val summaryTransactionId: String,
)

// ── Fund structural RPCs (E5.6–E5.8) ────────────────────────────────────────

/** Parameters for `rpc_create_fund` (E5.6). */
@Serializable
data class CreateFundParams(
    @SerialName("p_portfolio_id")           val portfolioId: String,
    @SerialName("p_name")                   val name: String,
    @SerialName("p_initial_amount_agoras")  val initialAmountAgoras: Long,
    @SerialName("p_operation_id")           val operationId: String,
    @SerialName("p_summary_transaction_id") val summaryTransactionId: String,
)

/** Parameters for `rpc_rename_fund` (E5.7). */
@Serializable
data class RenameFundParams(
    @SerialName("p_portfolio_id")           val portfolioId: String,
    @SerialName("p_fund_id")                val fundId: String,
    @SerialName("p_new_name")               val newName: String,
    @SerialName("p_operation_id")           val operationId: String,
    @SerialName("p_summary_transaction_id") val summaryTransactionId: String,
)

/** Parameters for `rpc_delete_fund` (E5.8). */
@Serializable
data class DeleteFundParams(
    @SerialName("p_portfolio_id")           val portfolioId: String,
    @SerialName("p_fund_id")                val fundId: String,
    @SerialName("p_operation_id")           val operationId: String,
    @SerialName("p_summary_transaction_id") val summaryTransactionId: String,
)

// ── rpc_commit_fund_operation (E6.7) — used by deposit/withdraw/transfer/revalue/undo ──

/**
 * Single detail row inside [CommitFundOperationParams.details]. Field names match the
 * keys the server reads via `jsonb_array_elements(p_details)` (see migration 006/009).
 */
@Serializable
data class CommitFundOperationDetail(
    @SerialName("transaction_id")       val transactionId: String,
    @SerialName("transaction_type")     val transactionType: String,
    @SerialName("fund_id")              val fundId: String,
    @SerialName("amount_agoras")        val amountAgoras: Long,
    @SerialName("undo_of_operation_id") val undoOfOperationId: String? = null,
)

/**
 * Parameters for `rpc_commit_fund_operation` (E6.7) — the shared engine for deposit,
 * withdrawal, transfer, revaluation and undo. The detail rows in [details] describe the
 * per-fund money changes the server applies atomically.
 */
@Serializable
data class CommitFundOperationParams(
    @SerialName("p_portfolio_id")             val portfolioId: String,
    @SerialName("p_operation_id")             val operationId: String,
    @SerialName("p_summary_transaction_id")   val summaryTransactionId: String,
    @SerialName("p_summary_transaction_type") val summaryTransactionType: String,
    @SerialName("p_summary_text")             val summaryText: String,
    @SerialName("p_details")                  val details: List<CommitFundOperationDetail>,
    @SerialName("p_note")                     val note: String? = null,
    @SerialName("p_created_by_device_id")     val createdByDeviceId: String? = null,
    @SerialName("p_client_app")               val clientApp: String? = "android",
    @SerialName("p_client_version")           val clientVersion: String? = null,
    @SerialName("p_undo_of_operation_id")     val undoOfOperationId: String? = null,
)

// ── Scheduled deposit RPCs (E8.7–E8.9) ──────────────────────────────────────

/**
 * Parameters for `rpc_upsert_scheduled_deposit` (E8.7). Pass [scheduledDepositId] = null
 * to create, or a value to update. The same call also enables/disables via [isEnabled] —
 * there is no separate enable/disable RPC.
 */
@Serializable
data class UpsertScheduledDepositParams(
    @SerialName("p_portfolio_id")        val portfolioId: String,
    @SerialName("p_fund_id")             val fundId: String,
    @SerialName("p_name")                val name: String,
    @SerialName("p_amount_agoras")       val amountAgoras: Long,
    @SerialName("p_schedule_kind")       val scheduleKind: String,
    @SerialName("p_is_enabled")          val isEnabled: Boolean,
    @SerialName("p_note")                val note: String? = null,
    @SerialName("p_time_of_day_minutes") val timeOfDayMinutes: Int? = null,
    @SerialName("p_weekday_mask")        val weekdayMask: Int? = null,
    @SerialName("p_day_of_month")        val dayOfMonth: Int? = null,
    @SerialName("p_next_run_at_utc")     val nextRunAtUtc: String? = null,
    @SerialName("p_scheduled_deposit_id") val scheduledDepositId: String? = null,
)

/** Parameters for `rpc_delete_scheduled_deposit` (E8.8). */
@Serializable
data class DeleteScheduledDepositParams(
    @SerialName("p_scheduled_deposit_id") val scheduledDepositId: String,
)

/**
 * Parameters for `rpc_execute_due_scheduled_deposits` (E8.9) — the device-triggered
 * exactly-once execution call. [nowUtc] is the current UTC instant; the server uses it as
 * the upper bound when selecting due occurrences.
 */
@Serializable
data class ExecuteDueScheduledDepositsParams(
    @SerialName("p_portfolio_id") val portfolioId: String,
    @SerialName("p_now_utc")      val nowUtc: String,
    @SerialName("p_device_id")    val deviceId: String,
)

/**
 * Shape of `rpc_upsert_scheduled_deposit`'s return value:
 *   { "scheduled_deposit_id": "<uuid>", "next_run_at_utc": "<iso>" }
 *
 * `JsonElement` would also work, but a typed class with `@SerialName` makes the contract
 * explicit. Mirrors how the desktop parses the JObject in `SupabaseScheduledDepositService.cs`.
 */
@Serializable
data class UpsertScheduledDepositResult(
    @SerialName("scheduled_deposit_id") val scheduledDepositId: String,
    @SerialName("next_run_at_utc")      val nextRunAtUtc: String? = null,
)

/**
 * Untyped wrapper for the elements of `rpc_execute_due_scheduled_deposits`'s array return.
 *
 * We deliberately don't bind to any specific field because v1 only consumes the count
 * (`array.size`). Each element describes one executed occurrence and the shape may evolve
 * on the server; the [unused] catch-all lets serialization succeed regardless.
 */
@Serializable
data class ExecutedOccurrence(val unused: JsonElement? = null)
