package com.virtualfunds.data.repository

import com.virtualfunds.data.dto.DeletedFundDto
import com.virtualfunds.data.dto.FundDto
import com.virtualfunds.data.dto.TransactionDto
import com.virtualfunds.data.error.RpcErrorMapper
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundFilterOption
import com.virtualfunds.domain.model.RecordKind
import com.virtualfunds.domain.model.TransactionDetailItem
import com.virtualfunds.domain.model.TransactionGroup
import com.virtualfunds.domain.model.TransactionType
import com.virtualfunds.domain.repository.TransactionRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed implementation of [TransactionRepository] (PR-7, E7).
 *
 * Mirrors `SupabaseTransactionService.cs`. Reads transactions + deleted_funds via
 * Postgrest, groups by `operation_id`, resolves fund names per E7.7, and computes
 * undoability across the loaded set per E6.12.
 *
 * History is read-only — there is no mutation path here. The append-only invariant is
 * enforced at the database level by the table's grant policy (E4.3.4).
 */
// @Singleton tells Hilt to keep one shared instance so the SupabaseClient (and its
// connection pool / Auth session) is shared across all repositories.
@Singleton
class SupabaseTransactionRepository
    // @Inject on the constructor lets Hilt's generated factory provide [client] from
    // the DI graph (SupabaseModule.provideSupabaseClient).
    @Inject constructor(
    private val client: SupabaseClient,
) : TransactionRepository {

    /** Generic fallback label when a fund_id can't be resolved (E7.7 step 3). */
    private companion object {
        const val DELETED_FUND_LABEL = "(קרן שנמחקה)"

        /**
         * Set of summary `transaction_type` values whose original operations can be undone
         * (E6.12). Scheduled-deposit executions and the various detail-only types are
         * intentionally excluded.
         */
        val UNDOABLE_TYPES = setOf(
            TransactionType.FundDeposit,
            TransactionType.FundWithdrawal,
            TransactionType.Transfer,
            TransactionType.PortfolioRevalued,
        )
    }

    override suspend fun getHistory(
        portfolioId: String,
    ): AppResult<List<TransactionGroup>> = RpcErrorMapper.runCatchingMapped {
        // 1. Fetch all rows, sorted newest-first (E7.6).
        val allRows = client.from("transactions").select {
            filter { eq("portfolio_id", portfolioId) }
            order(column = "committed_at_utc", order = Order.DESCENDING)
            order(column = "operation_id", order = Order.ASCENDING)
            order(column = "transaction_id", order = Order.ASCENDING)
        }.decodeList<TransactionDto>()

        if (allRows.isEmpty()) return@runCatchingMapped emptyList()

        // 2. Build the fund-name lookup once for all rows in this history.
        val fundNameMap = buildFundNameMap(portfolioId, allRows)

        // 3. Collect every operation_id referenced via undo_of_operation_id so we can
        //    mark already-undone operations as non-undoable (E6.12 detection).
        val undoneOperationIds: Set<String> = allRows
            .mapNotNullTo(mutableSetOf()) { it.undoOfOperationId }

        // 4. Group by operation_id and build display models.
        val groups = allRows
            .groupBy { it.operationId }
            .map { (operationId, rows) -> buildGroup(operationId, rows, fundNameMap, undoneOperationIds) }
            .sortedWith(
                compareByDescending<TransactionGroup> { it.committedAtUtc }
                    .thenBy { it.operationId }
            )

        groups
    }

    override suspend fun getFundFilterOptions(
        portfolioId: String,
    ): AppResult<List<FundFilterOption>> = RpcErrorMapper.runCatchingMapped {
        val activeFunds = client.from("funds").select {
            filter { eq("portfolio_id", portfolioId) }
            order(column = "normalized_name", order = Order.ASCENDING)
        }.decodeList<FundDto>()

        val activeOptions = activeFunds.map { FundFilterOption(it.fundId, it.name) }
        val activeIds = activeFunds.mapTo(mutableSetOf()) { it.fundId }

        // Tombstoned funds that aren't somehow also still active.
        val deletedFunds = client.from("deleted_funds").select {
            filter { eq("portfolio_id", portfolioId) }
            order(column = "name", order = Order.ASCENDING)
        }.decodeList<DeletedFundDto>()

        val deletedOptions = deletedFunds
            .filterNot { it.fundId in activeIds }
            .map { FundFilterOption(it.fundId, "${it.name} (נמחקה)") }

        activeOptions + deletedOptions
    }

    /**
     * Resolves every `fund_id` referenced in [transactions] to a display name per E7.7:
     *   1. current fund name (from `funds`)
     *   2. tombstone name (from `deleted_funds`)
     *   3. generic label
     */
    private suspend fun buildFundNameMap(
        portfolioId: String,
        transactions: List<TransactionDto>,
    ): Map<String, String> {
        val fundIds = transactions.mapNotNullTo(mutableSetOf()) { it.fundId }
        if (fundIds.isEmpty()) return emptyMap()

        val nameMap = mutableMapOf<String, String>()

        val activeFunds = client.from("funds").select {
            filter { eq("portfolio_id", portfolioId) }
        }.decodeList<FundDto>()
        for (f in activeFunds) nameMap[f.fundId] = f.name

        val missing = fundIds - nameMap.keys
        if (missing.isNotEmpty()) {
            val deleted = client.from("deleted_funds").select {
                filter { eq("portfolio_id", portfolioId) }
            }.decodeList<DeletedFundDto>()
            for (d in deleted) nameMap.putIfAbsent(d.fundId, d.name)
        }

        // Step 3 — generic label for anything still unresolved.
        for (id in fundIds) nameMap.putIfAbsent(id, DELETED_FUND_LABEL)

        return nameMap
    }

    /**
     * Builds one [TransactionGroup] from a slice of rows sharing the same operation_id.
     * Expects exactly one Summary row and zero or more Detail rows (E7.2). Unrecognised
     * `transaction_type` strings cause that detail row to be dropped (defensive).
     */
    private fun buildGroup(
        operationId: String,
        rows: List<TransactionDto>,
        fundNameMap: Map<String, String>,
        undoneOperationIds: Set<String>,
    ): TransactionGroup {
        val summary = rows.firstOrNull { it.recordKind == RecordKind.Summary.wireValue }
        val fallback = rows.first()

        val details = rows
            .filter { it.recordKind == RecordKind.Detail.wireValue }
            .mapNotNull { r ->
                val fundId = r.fundId ?: return@mapNotNull null
                val type = TransactionType.fromWire(r.transactionType) ?: return@mapNotNull null
                TransactionDetailItem(
                    fundId = fundId,
                    fundName = fundNameMap[fundId] ?: DELETED_FUND_LABEL,
                    transactionType = type,
                    amountAgoras = r.amountAgoras,
                    beforeBalanceAgoras = r.beforeBalanceAgoras,
                    afterBalanceAgoras = r.afterBalanceAgoras,
                )
            }

        // Use the summary row's type if it parses; otherwise the fallback row's type.
        // If neither parses, default to Undo to keep the type non-null (very rare —
        // would imply a row the client is too old to understand).
        val transactionType =
            summary?.let { TransactionType.fromWire(it.transactionType) }
                ?: TransactionType.fromWire(fallback.transactionType)
                ?: TransactionType.Undo

        val committedAtUtc = Instant.parse(summary?.committedAtUtc ?: fallback.committedAtUtc)

        // E6.12: undoable iff the summary type is one of [UNDOABLE_TYPES] AND no other
        // operation references this operation_id via undo_of_operation_id.
        val isUndoable =
            transactionType in UNDOABLE_TYPES && operationId !in undoneOperationIds

        return TransactionGroup(
            operationId = operationId,
            committedAtUtc = committedAtUtc,
            transactionType = transactionType,
            summaryText = summary?.summaryText,
            undoOfOperationId = summary?.undoOfOperationId,
            amountAgoras = summary?.amountAgoras ?: 0L,
            details = details,
            isUndoable = isUndoable,
        )
    }
}
