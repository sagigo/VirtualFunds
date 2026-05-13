package com.virtualfunds.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format row from the `transactions` table (E4.3.4). The mapping to the typed
 * [com.virtualfunds.domain.model.Transaction] happens in [com.virtualfunds.data.repository.SupabaseTransactionRepository]
 * because it requires resolving the [com.virtualfunds.domain.model.TransactionType] / [com.virtualfunds.domain.model.RecordKind]
 * enums from their wire strings.
 *
 * Unrecognised `record_kind` or `transaction_type` strings cause the row to be skipped at
 * the mapping layer rather than failing the entire history query. Newer server fields
 * (`metadata`, `created_by_device_id`, `client_app`, `client_version`) are intentionally
 * omitted — we don't use them in v1 reads.
 */
@Serializable
data class TransactionDto(
    @SerialName("transaction_id")        val transactionId: String,
    @SerialName("portfolio_id")          val portfolioId: String,
    @SerialName("operation_id")          val operationId: String,
    @SerialName("committed_at_utc")      val committedAtUtc: String,
    @SerialName("record_kind")           val recordKind: String,
    @SerialName("transaction_type")      val transactionType: String,
    @SerialName("fund_id")               val fundId: String? = null,
    @SerialName("amount_agoras")         val amountAgoras: Long,
    @SerialName("before_balance_agoras") val beforeBalanceAgoras: Long? = null,
    @SerialName("after_balance_agoras")  val afterBalanceAgoras: Long? = null,
    @SerialName("undo_of_operation_id")  val undoOfOperationId: String? = null,
    @SerialName("summary_text")          val summaryText: String? = null,
    @SerialName("note")                  val note: String? = null,
)
