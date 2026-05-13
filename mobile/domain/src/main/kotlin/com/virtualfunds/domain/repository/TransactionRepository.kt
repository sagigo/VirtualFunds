package com.virtualfunds.domain.repository

import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.FundFilterOption
import com.virtualfunds.domain.model.TransactionGroup

/**
 * Read-only access to the immutable transaction history (PR-7, E7).
 *
 * Mirrors `desktop/VirtualFunds.Core/Services/ITransactionService.cs`. History is
 * append-only — mutations are written by other RPCs (fund operations, structural RPCs).
 * This repository never writes.
 */
interface TransactionRepository {

    /**
     * Loads full history for a portfolio, grouped by `operation_id` and sorted newest-first
     * (E7.6). Fund names are resolved per E7.7 (current → tombstone → generic label).
     *
     * Each [TransactionGroup.isUndoable] flag is computed by scanning all loaded groups for
     * referenced `undo_of_operation_id` values (E6.12).
     */
    suspend fun getHistory(portfolioId: String): AppResult<List<TransactionGroup>>

    /**
     * Returns the funds available in the history filter dropdown — active funds first,
     * then tombstoned funds annotated with "(נמחקה)".
     */
    suspend fun getFundFilterOptions(portfolioId: String): AppResult<List<FundFilterOption>>
}
