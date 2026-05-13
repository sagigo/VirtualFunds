package com.virtualfunds.domain.model

import java.time.Instant

/**
 * A single row from the `transactions` table — the immutable audit log (E4.3.4).
 * Mirrors `Transaction.cs`.
 *
 * Every operation produces exactly one [RecordKind.Summary] row and zero or more
 * [RecordKind.Detail] rows, all sharing the same [operationId] (E7.2).
 */
data class Transaction(
    val transactionId: String,
    val portfolioId: String,
    val operationId: String,
    val committedAtUtc: Instant,
    val recordKind: RecordKind,
    val transactionType: TransactionType,
    val fundId: String?,
    val amountAgoras: Long,
    val beforeBalanceAgoras: Long?,
    val afterBalanceAgoras: Long?,
    val undoOfOperationId: String?,
    val summaryText: String?,
    val note: String?,
)

/**
 * Display-oriented group of all transaction rows for one logical operation (E7.2).
 *
 * Contains the summary row's fields at the top level and the detail rows under [details].
 * Mirrors `TransactionGroup.cs`.
 *
 * [isUndoable] is computed at group-construction time by the repository after collecting
 * all `undo_of_operation_id` references across the loaded history (E6.12 detection).
 */
data class TransactionGroup(
    val operationId: String,
    val committedAtUtc: Instant,
    val transactionType: TransactionType,
    val summaryText: String?,
    val undoOfOperationId: String?,
    val amountAgoras: Long,
    val details: List<TransactionDetailItem>,
    val isUndoable: Boolean,
)

/**
 * Per-fund detail row inside a [TransactionGroup] (E7.2). Mirrors `TransactionDetailItem.cs`.
 *
 * [fundName] is resolved per E7.7: current fund → tombstone → generic deleted-fund label.
 * The resolution happens in the repository, not here.
 */
data class TransactionDetailItem(
    val fundId: String,
    val fundName: String,
    val transactionType: TransactionType,
    val amountAgoras: Long,
    val beforeBalanceAgoras: Long?,
    val afterBalanceAgoras: Long?,
)

/**
 * A fund available for filtering in the history view (active + tombstoned).
 * Mirrors `FundFilterOption` in `ITransactionService.cs`.
 */
data class FundFilterOption(
    val fundId: String,
    val displayName: String,
)
