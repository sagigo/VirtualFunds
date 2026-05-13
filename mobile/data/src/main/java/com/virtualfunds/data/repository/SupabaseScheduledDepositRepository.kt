package com.virtualfunds.data.repository

import com.virtualfunds.data.dto.DeleteScheduledDepositParams
import com.virtualfunds.data.dto.ExecuteDueScheduledDepositsParams
import com.virtualfunds.data.dto.ExecutedOccurrence
import com.virtualfunds.data.dto.FundDto
import com.virtualfunds.data.dto.ScheduledDepositDto
import com.virtualfunds.data.dto.UpsertScheduledDepositParams
import com.virtualfunds.data.dto.UpsertScheduledDepositResult
import com.virtualfunds.data.error.RpcErrorMapper
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDepositListItem
import com.virtualfunds.domain.repository.ScheduledDepositRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed implementation of [ScheduledDepositRepository] (PR-8, E8).
 *
 * Mirrors `SupabaseScheduledDepositService.cs`. Reads use Postgrest; CRUD and execution
 * go through dedicated RPCs.
 *
 * The repository does *not* compute `next_run_at_utc` for recurring schedules —
 * `rpc_upsert_scheduled_deposit` does that server-side (E8.12). For `OneTime` schedules
 * the caller supplies `nextRunAtUtc` directly (already converted from Israel time).
 */
// @Singleton tells Hilt to keep one shared instance so the SupabaseClient (and its
// connection pool / Auth session) is shared across all repositories.
@Singleton
class SupabaseScheduledDepositRepository
    // @Inject on the constructor lets Hilt's generated factory provide [client] from
    // the DI graph (SupabaseModule.provideSupabaseClient).
    @Inject constructor(
    private val client: SupabaseClient,
) : ScheduledDepositRepository {

    override suspend fun getScheduledDeposits(
        portfolioId: String,
    ): AppResult<List<ScheduledDepositListItem>> = RpcErrorMapper.runCatchingMapped {
        val deposits = client.from("scheduled_deposits").select {
            filter { eq("portfolio_id", portfolioId) }
            order(column = "created_at_utc", order = Order.DESCENDING)
        }.decodeList<ScheduledDepositDto>()

        if (deposits.isEmpty()) return@runCatchingMapped emptyList()

        // Resolve fund names. We don't go through FundRepository here to avoid an
        // unnecessary AppResult unwrap and to keep this repo independent of others.
        val funds = client.from("funds").select {
            filter { eq("portfolio_id", portfolioId) }
        }.decodeList<FundDto>()
        val fundNameMap: Map<String, String> = funds.associateBy({ it.fundId }, { it.name })

        deposits.map { d ->
            ScheduledDepositListItem(
                scheduledDepositId = d.scheduledDepositId,
                portfolioId = d.portfolioId,
                fundId = d.fundId,
                fundName = fundNameMap[d.fundId] ?: "—",
                name = d.name,
                note = d.note,
                isEnabled = d.isEnabled,
                amountAgoras = d.amountAgoras,
                scheduleKind = ScheduleKind.fromWire(d.scheduleKind) ?: ScheduleKind.OneTime,
                timeOfDayMinutes = d.timeOfDayMinutes,
                weekdayMask = d.weekdayMask,
                dayOfMonth = d.dayOfMonth,
                nextRunAtUtc = Instant.parse(d.nextRunAtUtc),
            )
        }
    }

    override suspend fun upsertScheduledDeposit(
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
    ): AppResult<String> = RpcErrorMapper.runCatchingMapped {
        val result = client.postgrest.rpc(
            "rpc_upsert_scheduled_deposit",
            parameters = UpsertScheduledDepositParams(
                portfolioId = portfolioId,
                fundId = fundId,
                name = name,
                amountAgoras = amountAgoras,
                scheduleKind = scheduleKind.wireValue,
                isEnabled = isEnabled,
                note = note,
                timeOfDayMinutes = timeOfDayMinutes,
                weekdayMask = weekdayMask,
                dayOfMonth = dayOfMonth,
                // Instant.toString() emits canonical ISO-8601 with trailing Z — what Postgres
                // expects for `timestamptz`.
                nextRunAtUtc = nextRunAtUtc?.toString(),
                scheduledDepositId = scheduledDepositId,
            ),
        )
        // RPC returns { "scheduled_deposit_id": "...", "next_run_at_utc": "..." } as jsonb.
        result.decodeAs<UpsertScheduledDepositResult>().scheduledDepositId
    }

    override suspend fun deleteScheduledDeposit(
        scheduledDepositId: String,
    ): AppResult<Unit> = RpcErrorMapper.runCatchingMapped {
        client.postgrest.rpc(
            "rpc_delete_scheduled_deposit",
            parameters = DeleteScheduledDepositParams(scheduledDepositId = scheduledDepositId),
        )
        Unit
    }

    override suspend fun executeDueDeposits(
        portfolioId: String,
        deviceId: String,
    ): AppResult<Int> = RpcErrorMapper.runCatchingMapped {
        val result = client.postgrest.rpc(
            "rpc_execute_due_scheduled_deposits",
            parameters = ExecuteDueScheduledDepositsParams(
                portfolioId = portfolioId,
                nowUtc = Instant.now().toString(),
                deviceId = deviceId,
            ),
        )
        // The RPC returns a jsonb array of executed occurrences. We only need the count;
        // the array elements describe each executed occurrence and may evolve over time.
        result.decodeList<ExecutedOccurrence>().size
    }
}
