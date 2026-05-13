package com.virtualfunds.domain.repository

import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.PortfolioListItem

/**
 * Portfolio CRUD operations (PR-4, E5.3–E5.5).
 *
 * Mirrors `desktop/VirtualFunds.Core/Services/IPortfolioService.cs`. All mutations execute
 * via Supabase RPC and produce transaction history on the server (E1.4, E4.5).
 *
 * Unlike the desktop service, methods return [AppResult] rather than throwing — every call
 * site must handle both the success and failure branches (mobile/CLAUDE.md §Error Handling).
 */
interface PortfolioRepository {

    /**
     * Loads all active (non-closed) portfolios for the authenticated user, sorted by
     * `normalized_name` ascending, each with its computed total balance.
     *
     * Total is `SUM(funds.balance_agoras)` per portfolio — derived, not stored (E5.9).
     */
    suspend fun getActivePortfolios(): AppResult<List<PortfolioListItem>>

    /**
     * Creates a new portfolio (E5.3). The name is trimmed and validated for case-insensitive
     * uniqueness on the server.
     *
     * Returns the new portfolio_id on success.
     *
     * Failures: [com.virtualfunds.domain.AppError.Validation.EmptyName],
     * [com.virtualfunds.domain.AppError.Validation.DuplicateName].
     */
    suspend fun createPortfolio(name: String): AppResult<String>

    /**
     * Renames an existing active portfolio (E5.4).
     *
     * Failures: EmptyName, DuplicateName, PortfolioClosed, NotFound.
     */
    suspend fun renamePortfolio(portfolioId: String, newName: String): AppResult<Unit>

    /**
     * Soft-deletes a portfolio (E5.5). Also disables all scheduled deposits in the
     * portfolio atomically.
     *
     * Failures: PortfolioClosed (already closed), NotFound.
     */
    suspend fun closePortfolio(portfolioId: String): AppResult<Unit>
}
