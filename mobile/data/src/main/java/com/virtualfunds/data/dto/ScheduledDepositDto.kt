package com.virtualfunds.data.dto

import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDeposit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire-format row from the `scheduled_deposits` table (E4.3.5).
 *
 * `schedule_kind` is stored as a text column (`'OneTime' | 'Daily' | 'Weekly' | 'Monthly'`),
 * so we keep the DTO field as `String` and convert to the typed [ScheduleKind] in
 * [toDomain]. Unknown values fall back to [ScheduleKind.OneTime] to keep the type non-null —
 * the realistic case (server adds a new kind we don't recognise) should be rare, and the
 * row will still render with a generic schedule string in the UI.
 */
@Serializable
data class ScheduledDepositDto(
    @SerialName("scheduled_deposit_id") val scheduledDepositId: String,
    @SerialName("portfolio_id")         val portfolioId: String,
    @SerialName("fund_id")              val fundId: String,
    @SerialName("name")                 val name: String,
    @SerialName("note")                 val note: String? = null,
    @SerialName("is_enabled")           val isEnabled: Boolean,
    @SerialName("amount_agoras")        val amountAgoras: Long,
    @SerialName("schedule_kind")        val scheduleKind: String,
    @SerialName("time_of_day_minutes")  val timeOfDayMinutes: Int? = null,
    @SerialName("weekday_mask")         val weekdayMask: Int? = null,
    @SerialName("day_of_month")         val dayOfMonth: Int? = null,
    @SerialName("next_run_at_utc")      val nextRunAtUtc: String,
    @SerialName("created_at_utc")       val createdAtUtc: String,
    @SerialName("updated_at_utc")       val updatedAtUtc: String,
) {
    /**
     * Converts the wire DTO to the pure-domain [ScheduledDeposit]. Unknown `schedule_kind`
     * values fall back to [ScheduleKind.OneTime] so the type stays non-null; the row will
     * still render with a generic schedule string in the UI.
     */
    fun toDomain(): ScheduledDeposit = ScheduledDeposit(
        scheduledDepositId = scheduledDepositId,
        portfolioId = portfolioId,
        fundId = fundId,
        name = name,
        note = note,
        isEnabled = isEnabled,
        amountAgoras = amountAgoras,
        scheduleKind = ScheduleKind.fromWire(scheduleKind) ?: ScheduleKind.OneTime,
        timeOfDayMinutes = timeOfDayMinutes,
        weekdayMask = weekdayMask,
        dayOfMonth = dayOfMonth,
        nextRunAtUtc = Instant.parse(nextRunAtUtc),
        createdAtUtc = Instant.parse(createdAtUtc),
        updatedAtUtc = Instant.parse(updatedAtUtc),
    )
}
