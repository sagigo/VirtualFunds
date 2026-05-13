package com.virtualfunds.domain.repository

import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundListItem

/**
 * Fund CRUD and money operations within a portfolio (PR-5, PR-6, E5.6–E5.8, E6).
 *
 * Mirrors `desktop/VirtualFunds.Core/Services/IFundService.cs`. Reads use Postgrest;
 * mutations go through dedicated RPCs (structural) or `rpc_commit_fund_operation`
 * (money operations) per E5.2 / E6.7.
 *
 * Money-operation methods that succeed return the `operation_id` of the committed
 * operation — useful for ViewModels that want to register an undo entry.
 */
interface FundRepository {

    /**
     * Loads all funds for a portfolio, sorted by `normalized_name` ascending. Each item
     * carries its derived allocation percent (E5.9 / E5.10).
     */
    suspend fun getFunds(portfolioId: String): AppResult<List<FundListItem>>

    // ── Structural ───────────────────────────────────────────────────────────

    /**
     * Creates a new fund, optionally with an initial credit (E5.6).
     *
     * Returns the new fund_id. Failures: EmptyName, DuplicateName, NegativeAmount,
     * PortfolioClosed.
     */
    suspend fun createFund(
        portfolioId: String,
        name: String,
        initialAmountAgoras: Long,
    ): AppResult<String>

    /**
     * Renames a fund (E5.7). Failures: EmptyName, DuplicateName, PortfolioClosed, NotFound.
     */
    suspend fun renameFund(
        portfolioId: String,
        fundId: String,
        newName: String,
    ): AppResult<Unit>

    /**
     * Deletes a fund (E5.8). The fund must have a zero balance and no enabled scheduled
     * deposits targeting it (server-enforced).
     *
     * Failures: FundNotEmpty, FundHasEnabledScheduledDeposit, PortfolioClosed, NotFound.
     */
    suspend fun deleteFund(portfolioId: String, fundId: String): AppResult<Unit>

    // ── Money operations (E6.7–E6.10) ────────────────────────────────────────

    /**
     * Deposits a positive amount into a single fund (E6.8). Returns the operation_id.
     */
    suspend fun deposit(
        portfolioId: String,
        fundId: String,
        amountAgoras: Long,
    ): AppResult<String>

    /**
     * Withdraws a positive amount from a single fund (E6.9). The fund must have sufficient
     * balance (server-enforced). Returns the operation_id.
     *
     * Failures include [com.virtualfunds.domain.AppError.Invariant.NegativeBalance] when
     * the server rejects due to insufficient funds.
     */
    suspend fun withdraw(
        portfolioId: String,
        fundId: String,
        amountAgoras: Long,
    ): AppResult<String>

    /**
     * Transfers money between two distinct funds in the same portfolio (E6.10).
     * Source and destination must differ — enforced client-side via [SameFundTransfer].
     * Returns the operation_id.
     */
    suspend fun transfer(
        portfolioId: String,
        sourceFundId: String,
        destinationFundId: String,
        amountAgoras: Long,
    ): AppResult<String>

    /**
     * Revalues the portfolio total, scaling all fund balances proportionally (E6.11).
     *
     * Implementation fetches the current fund state first (for freshness), computes
     * per-fund deltas using banker's rounding (E6.4) + deterministic remainder
     * distribution (E6.5), then commits via `rpc_commit_fund_operation`.
     *
     * Returns the operation_id, or `null` if the operation is a no-op (same total or
     * all deltas zero after rounding).
     *
     * Failures: NegativeAmount (new_total ≤ 0), PortfolioTotalIsZero,
     * RevalueWouldZeroFund, PortfolioClosed.
     */
    suspend fun revaluePortfolio(
        portfolioId: String,
        newTotalAgoras: Long,
    ): AppResult<String?>

    /**
     * Undoes a previously committed operation by creating compensating rows (E6.12).
     *
     * Queries the original detail rows for the operation, negates each amount, and commits
     * a new operation with summary type "Undo" and `undo_of_operation_id` set to the original.
     *
     * Failures: PortfolioClosed, NegativeBalance (applying compensations would create a
     * negative balance).
     */
    suspend fun undoOperation(
        portfolioId: String,
        originalOperationId: String,
    ): AppResult<Unit>
}
