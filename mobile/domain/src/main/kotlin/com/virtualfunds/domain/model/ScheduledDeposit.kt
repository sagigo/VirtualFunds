package com.virtualfunds.domain.model

import java.time.Instant

/**
 * A scheduled deposit rule (E4.3.5, E8). Mirrors `ScheduledDeposit.cs`.
 *
 * Field availability depends on [scheduleKind] (E8.2):
 *   - `OneTime`  → all of [timeOfDayMinutes]/[weekdayMask]/[dayOfMonth] are null;
 *                  [nextRunAtUtc] is the single desired execution time
 *   - `Daily`    → [timeOfDayMinutes] required; others null
 *   - `Weekly`   → [timeOfDayMinutes] and [weekdayMask] required; [dayOfMonth] null
 *   - `Monthly`  → [timeOfDayMinutes] and [dayOfMonth] required; [weekdayMask] null
 *
 * The schedule fields are interpreted in Israel time (`Asia/Jerusalem`) per E8.3 / E2.1.
 */
data class ScheduledDeposit(
    val scheduledDepositId: String,
    val portfolioId: String,
    val fundId: String,
    val name: String,
    val note: String?,
    val isEnabled: Boolean,
    val amountAgoras: Long,
    val scheduleKind: ScheduleKind,
    val timeOfDayMinutes: Int?,
    val weekdayMask: Int?,
    val dayOfMonth: Int?,
    val nextRunAtUtc: Instant,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)

/**
 * Display-oriented scheduled deposit entry for the list screen. Mirrors `ScheduledDepositListItem.cs`.
 *
 * [fundName] is resolved from the funds map (active funds only) — UI strings (status label,
 * formatted schedule text) live in the UI layer where Compose / Hebrew resources can produce
 * them. We keep this type free of pre-formatted strings.
 */
data class ScheduledDepositListItem(
    val scheduledDepositId: String,
    val portfolioId: String,
    val fundId: String,
    val fundName: String,
    val name: String,
    val note: String?,
    val isEnabled: Boolean,
    val amountAgoras: Long,
    val scheduleKind: ScheduleKind,
    val timeOfDayMinutes: Int?,
    val weekdayMask: Int?,
    val dayOfMonth: Int?,
    val nextRunAtUtc: Instant,
)
