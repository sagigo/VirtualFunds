package com.virtualfunds.domain.repository

import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDepositListItem
import java.time.Instant

/**
 * Scheduled deposit CRUD and device-triggered execution (PR-8, E8).
 *
 * Mirrors `desktop/VirtualFunds.Core/Services/IScheduledDepositService.cs`. CRUD calls
 * dedicated RPCs; execution calls `rpc_execute_due_scheduled_deposits` which itself
 * pipes through `rpc_commit_fund_operation` for the money mutation (E8.9).
 */
interface ScheduledDepositRepository {

    /**
     * Loads all scheduled deposits for a portfolio (E4.6), ordered by `created_at_utc` desc.
     * Each item carries its target fund's display name resolved from the funds table.
     */
    suspend fun getScheduledDeposits(
        portfolioId: String,
    ): AppResult<List<ScheduledDepositListItem>>

    /**
     * Creates or updates a scheduled deposit via `rpc_upsert_scheduled_deposit` (E8.7).
     * Pass [scheduledDepositId] to update, or `null` to create.
     *
     * This is also the mechanism for enabling and disabling — pass [isEnabled] = true / false.
     * There is no separate enable/disable RPC.
     *
     * Schedule-field expectations (E8.2):
     *   - OneTime → [nextRunAtUtc] is the single execution time
     *   - Daily   → [timeOfDayMinutes] required; others null
     *   - Weekly  → [timeOfDayMinutes] and [weekdayMask] required; [dayOfMonth] null
     *   - Monthly → [timeOfDayMinutes] and [dayOfMonth] required; [weekdayMask] null
     *
     * Failures: InvalidScheduleFields, InvalidScheduleKind, NegativeAmount, PortfolioClosed.
     */
    suspend fun upsertScheduledDeposit(
        portfolioId: String,
        fundId: String,
        name: String,
        amountAgoras: Long,
        scheduleKind: ScheduleKind,
        isEnabled: Boolean,
        note: String?,
        timeOfDayMinutes: Int?,
        weekdayMask: Int?,
        dayOfMonth: Int?,
        nextRunAtUtc: Instant?,
        scheduledDepositId: String?,
    ): AppResult<String>

    /** Deletes a scheduled deposit and its occurrence rows (E8.8). */
    suspend fun deleteScheduledDeposit(scheduledDepositId: String): AppResult<Unit>

    /**
     * Triggers exactly-once execution of all due scheduled deposits for a portfolio (E8.9).
     *
     * Calls `rpc_execute_due_scheduled_deposits`; the server claims occurrences, commits
     * the money mutation, advances `next_run_at_utc`, and returns the executed list.
     *
     * Returns the number of occurrences executed. A return of 0 is normal (nothing was due).
     */
    suspend fun executeDueDeposits(
        portfolioId: String,
        deviceId: String,
    ): AppResult<Int>
}
