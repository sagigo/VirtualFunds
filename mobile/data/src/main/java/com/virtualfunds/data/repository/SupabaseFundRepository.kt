package com.virtualfunds.data.repository

import com.virtualfunds.data.dto.CommitFundOperationDetail
import com.virtualfunds.data.dto.CommitFundOperationParams
import com.virtualfunds.data.dto.CreateFundParams
import com.virtualfunds.data.dto.DeleteFundParams
import com.virtualfunds.data.dto.FundDto
import com.virtualfunds.data.dto.RenameFundParams
import com.virtualfunds.data.dto.TransactionDto
import com.virtualfunds.data.error.RpcErrorMapper
import com.virtualfunds.data.util.Ids
import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.bankersRound
import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.model.TransactionType
import com.virtualfunds.domain.repository.FundRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed implementation of [FundRepository] (PR-5, PR-6, E5.6–E5.8, E6).
 *
 * Mirrors `SupabaseFundService.cs`. Structural mutations call dedicated RPCs; money
 * operations go through `rpc_commit_fund_operation` (E6.7).
 *
 * Revaluation does all proportional-scaling math client-side (banker's rounding +
 * deterministic remainder distribution) before calling the commit RPC, mirroring the
 * desktop implementation step-by-step (E6.4, E6.5, E6.11).
 */
// @Singleton tells Hilt to keep one shared instance — all collaborators see the same
// SupabaseClient and reuse its connection pool / Auth session.
@Singleton
class SupabaseFundRepository
    // @Inject on the constructor lets Hilt's generated factory provide the [client]
    // dependency from the DI graph (SupabaseModule.provideSupabaseClient).
    @Inject constructor(
    private val client: SupabaseClient,
) : FundRepository {

    override suspend fun getFunds(portfolioId: String): AppResult<List<FundListItem>> =
        RpcErrorMapper.runCatchingMapped {
            val funds = client.from("funds").select {
                filter { eq("portfolio_id", portfolioId) }
                order(column = "normalized_name", order = Order.ASCENDING)
            }.decodeList<FundDto>()

            if (funds.isEmpty()) return@runCatchingMapped emptyList()

            val total = funds.sumOf { it.balanceAgoras }

            funds.map { f ->
                FundListItem(
                    fundId = f.fundId,
                    name = f.name,
                    balanceAgoras = f.balanceAgoras,
                    allocationPercent = if (total > 0)
                        f.balanceAgoras.toDouble() / total * 100.0
                    else 0.0,
                    createdAtUtc = Instant.parse(f.createdAtUtc),
                )
            }
        }

    // ── Structural ───────────────────────────────────────────────────────────

    override suspend fun createFund(
        portfolioId: String,
        name: String,
        initialAmountAgoras: Long,
    ): AppResult<String> = RpcErrorMapper.runCatchingMapped {
        client.postgrest.rpc(
            "rpc_create_fund",
            parameters = CreateFundParams(
                portfolioId = portfolioId,
                name = name,
                initialAmountAgoras = initialAmountAgoras,
                operationId = Ids.newOperationId(),
                summaryTransactionId = Ids.newTransactionId(),
            ),
        ).decodeAs<String>()
    }

    override suspend fun renameFund(
        portfolioId: String,
        fundId: String,
        newName: String,
    ): AppResult<Unit> = RpcErrorMapper.runCatchingMapped {
        client.postgrest.rpc(
            "rpc_rename_fund",
            parameters = RenameFundParams(
                portfolioId = portfolioId,
                fundId = fundId,
                newName = newName,
                operationId = Ids.newOperationId(),
                summaryTransactionId = Ids.newTransactionId(),
            ),
        )
        Unit
    }

    override suspend fun deleteFund(
        portfolioId: String,
        fundId: String,
    ): AppResult<Unit> = RpcErrorMapper.runCatchingMapped {
        client.postgrest.rpc(
            "rpc_delete_fund",
            parameters = DeleteFundParams(
                portfolioId = portfolioId,
                fundId = fundId,
                operationId = Ids.newOperationId(),
                summaryTransactionId = Ids.newTransactionId(),
            ),
        )
        Unit
    }

    // ── Money operations (E6.7–E6.10) ────────────────────────────────────────

    override suspend fun deposit(
        portfolioId: String,
        fundId: String,
        amountAgoras: Long,
    ): AppResult<String> {
        if (amountAgoras <= 0) return AppResult.Failure(AppError.Validation.NegativeAmount)
        return commitFundOperation(
            portfolioId = portfolioId,
            summaryTransactionType = TransactionType.FundDeposit,
            summaryText = "הפקדה לקרן",
            details = listOf(
                CommitFundOperationDetail(
                    transactionId = Ids.newTransactionId(),
                    transactionType = TransactionType.FundDeposit.wireValue,
                    fundId = fundId,
                    amountAgoras = amountAgoras,
                ),
            ),
        )
    }

    override suspend fun withdraw(
        portfolioId: String,
        fundId: String,
        amountAgoras: Long,
    ): AppResult<String> {
        if (amountAgoras <= 0) return AppResult.Failure(AppError.Validation.NegativeAmount)
        return commitFundOperation(
            portfolioId = portfolioId,
            summaryTransactionType = TransactionType.FundWithdrawal,
            summaryText = "משיכה מקרן",
            details = listOf(
                CommitFundOperationDetail(
                    transactionId = Ids.newTransactionId(),
                    transactionType = TransactionType.FundWithdrawal.wireValue,
                    fundId = fundId,
                    amountAgoras = -amountAgoras,
                ),
            ),
        )
    }

    override suspend fun transfer(
        portfolioId: String,
        sourceFundId: String,
        destinationFundId: String,
        amountAgoras: Long,
    ): AppResult<String> {
        if (sourceFundId == destinationFundId)
            return AppResult.Failure(AppError.Validation.SameFundTransfer)
        if (amountAgoras <= 0)
            return AppResult.Failure(AppError.Validation.NegativeAmount)

        return commitFundOperation(
            portfolioId = portfolioId,
            summaryTransactionType = TransactionType.Transfer,
            summaryText = "העברה בין קרנות",
            details = listOf(
                CommitFundOperationDetail(
                    transactionId = Ids.newTransactionId(),
                    transactionType = TransactionType.TransferDebit.wireValue,
                    fundId = sourceFundId,
                    amountAgoras = -amountAgoras,
                ),
                CommitFundOperationDetail(
                    transactionId = Ids.newTransactionId(),
                    transactionType = TransactionType.TransferCredit.wireValue,
                    fundId = destinationFundId,
                    amountAgoras = amountAgoras,
                ),
            ),
        )
    }

    override suspend fun revaluePortfolio(
        portfolioId: String,
        newTotalAgoras: Long,
    ): AppResult<String?> {
        if (newTotalAgoras <= 0) return AppResult.Failure(AppError.Validation.NegativeAmount)

        // 1. Fetch current fund state for freshness (server will re-validate after locks).
        val fundsResult = getFunds(portfolioId)
        val funds = when (fundsResult) {
            is AppResult.Success -> fundsResult.value
            is AppResult.Failure -> return AppResult.Failure(fundsResult.error)
        }

        if (funds.isEmpty()) return AppResult.Failure(AppError.Validation.PortfolioTotalIsZero)

        val oldTotalAgoras = funds.sumOf { it.balanceAgoras }
        if (oldTotalAgoras == 0L) return AppResult.Failure(AppError.Validation.PortfolioTotalIsZero)

        // 2. No-op when totals equal — don't log (E6.11 step 3).
        if (newTotalAgoras == oldTotalAgoras) return AppResult.Success(null)

        // 3. Compute provisional new balances using banker's rounding (E6.4).
        //    BigInteger numerator avoids overflow for newTotal * oldBalance up to ~2^63 each.
        data class WorkItem(val fundId: String, val oldBalance: Long, var newBalance: Long)

        val workItems = funds.map { f ->
            val numerator = newTotalAgoras.toBigInteger() * f.balanceAgoras.toBigInteger()
            WorkItem(
                fundId = f.fundId,
                oldBalance = f.balanceAgoras,
                newBalance = bankersRound(numerator, oldTotalAgoras),
            )
        }

        // 4. Compute remainder and fix deterministically (E6.5).
        val provisionalSum = workItems.sumOf { it.newBalance }
        val remainder = newTotalAgoras - provisionalSum

        val sorted = workItems.sortedBy { it.fundId }.toMutableList()

        when {
            remainder > 0 -> {
                for (i in 0 until remainder.toInt()) sorted[i].newBalance += 1
            }
            remainder < 0 -> {
                val absRemainder = (-remainder).toInt()
                var adjusted = 0
                var i = 0
                while (i < sorted.size && adjusted < absRemainder) {
                    if (sorted[i].newBalance > 0) {
                        sorted[i].newBalance -= 1
                        adjusted++
                    }
                    i++
                }
                // If we couldn't find enough qualifying funds the operation is invalid
                // (E6.5 edge case). Reject before any RPC call.
                if (adjusted < absRemainder)
                    return AppResult.Failure(AppError.Invariant.NegativeBalance)
            }
        }

        // 8b. Reject if any positive-balance fund got rounded to zero (E6.11 step 8b).
        if (sorted.any { it.oldBalance > 0 && it.newBalance == 0L })
            return AppResult.Failure(AppError.Validation.RevalueWouldZeroFund)

        // 9–10. Build detail rows for non-zero deltas only.
        val details = sorted.mapNotNull { w ->
            val delta = w.newBalance - w.oldBalance
            if (delta == 0L) return@mapNotNull null
            CommitFundOperationDetail(
                transactionId = Ids.newTransactionId(),
                transactionType = if (delta > 0)
                    TransactionType.RevaluationCredit.wireValue
                else
                    TransactionType.RevaluationDebit.wireValue,
                fundId = w.fundId,
                amountAgoras = delta,
            )
        }

        // All deltas zero after rounding — treat as no-op (same as desktop).
        if (details.isEmpty()) return AppResult.Success(null)

        // 11–12. Commit. Use the desktop's exact Hebrew template so server-side logs
        //        and history rows match across platforms.
        val summaryText = buildRevalueSummary(oldTotalAgoras, newTotalAgoras)

        return when (val committed = commitFundOperation(
            portfolioId = portfolioId,
            summaryTransactionType = TransactionType.PortfolioRevalued,
            summaryText = summaryText,
            details = details,
        )) {
            is AppResult.Success -> AppResult.Success(committed.value)
            is AppResult.Failure -> AppResult.Failure(committed.error)
        }
    }

    override suspend fun undoOperation(
        portfolioId: String,
        originalOperationId: String,
    ): AppResult<Unit> {
        // 2. Query original detail rows (E6.12 step 2).
        val rows = RpcErrorMapper.runCatchingMapped {
            client.from("transactions").select {
                filter {
                    eq("portfolio_id", portfolioId)
                    eq("operation_id", originalOperationId)
                    eq("record_kind", "Detail")
                }
            }.decodeList<TransactionDto>()
        }

        val originalDetails = when (rows) {
            is AppResult.Success -> rows.value
            is AppResult.Failure -> return AppResult.Failure(rows.error)
        }

        // 3–5. Build compensating details with negated amounts.
        val details = originalDetails.mapNotNull { d ->
            val fundId = d.fundId ?: return@mapNotNull null
            CommitFundOperationDetail(
                transactionId = Ids.newTransactionId(),
                transactionType = TransactionType.Undo.wireValue,
                fundId = fundId,
                amountAgoras = -d.amountAgoras,
                undoOfOperationId = originalOperationId,
            )
        }

        // 6. Commit with summary type Undo + undo_of_operation_id on the summary row.
        val res = commitFundOperation(
            portfolioId = portfolioId,
            summaryTransactionType = TransactionType.Undo,
            summaryText = "ביטול פעולה",
            details = details,
            undoOfOperationId = originalOperationId,
        )
        return when (res) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(res.error)
        }
    }

    /**
     * Shared call site for all `rpc_commit_fund_operation` invocations. Generates the
     * operation/transaction UUIDs, packages params, calls the RPC, and returns the
     * operation_id on success.
     */
    private suspend fun commitFundOperation(
        portfolioId: String,
        summaryTransactionType: TransactionType,
        summaryText: String,
        details: List<CommitFundOperationDetail>,
        undoOfOperationId: String? = null,
    ): AppResult<String> {
        val operationId = Ids.newOperationId()
        val summaryTransactionId = Ids.newTransactionId()

        return RpcErrorMapper.runCatchingMapped {
            client.postgrest.rpc(
                "rpc_commit_fund_operation",
                parameters = CommitFundOperationParams(
                    portfolioId = portfolioId,
                    operationId = operationId,
                    summaryTransactionId = summaryTransactionId,
                    summaryTransactionType = summaryTransactionType.wireValue,
                    summaryText = summaryText,
                    details = details,
                    undoOfOperationId = undoOfOperationId,
                ),
            )
            operationId
        }
    }

    /**
     * Reproduces the desktop's Hebrew template `עדכון שווי תיק מ-{old} ל-{new}` so the
     * server-stored summary text is byte-identical regardless of which client recorded it.
     */
    private fun buildRevalueSummary(oldTotalAgoras: Long, newTotalAgoras: Long): String {
        val oldFormatted = com.virtualfunds.domain.formatAgoras(oldTotalAgoras)
        val newFormatted = com.virtualfunds.domain.formatAgoras(newTotalAgoras)
        return "עדכון שווי תיק מ-$oldFormatted ל-$newFormatted"
    }
}

