package com.virtualfunds.ui.scheduled

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.virtualfunds.R
import com.virtualfunds.domain.formatIsraelDateTime
import com.virtualfunds.domain.formatTimeOfDay
import com.virtualfunds.domain.model.ScheduleKind
import com.virtualfunds.domain.model.ScheduledDepositListItem

/**
 * Hebrew display helpers for scheduled deposits. The pure-domain models deliberately carry no
 * pre-formatted strings (see `ScheduledDeposit.kt`), so the UI layer assembles them here from
 * string resources — mirroring the desktop `ScheduledDepositListItem` computed properties.
 */

/** String-resource id for a schedule kind's Hebrew label (E8.2, Appendix A). */
fun scheduleKindLabelRes(kind: ScheduleKind): Int = when (kind) {
    ScheduleKind.OneTime -> R.string.sd_kind_one_time
    ScheduleKind.Daily -> R.string.sd_kind_daily
    ScheduleKind.Weekly -> R.string.sd_kind_weekly
    ScheduleKind.Monthly -> R.string.sd_kind_monthly
}

/** A schedule kind's Hebrew label, e.g. "יומי". */
@Composable
fun scheduleKindLabel(kind: ScheduleKind): String = stringResource(scheduleKindLabelRes(kind))

/**
 * Human-readable Hebrew schedule summary, mirroring desktop `FormattedSchedule`:
 *   - Daily   → "יומי — 09:00"
 *   - Weekly  → "שבועי — א׳, ג׳ — 14:30"
 *   - Monthly → "חודשי — יום 15 — 08:00"
 *   - OneTime → "חד פעמי — 06/04/2026 09:00" (next run in Israel time)
 */
@Composable
fun scheduleSummary(item: ScheduledDepositListItem): String {
    val time = item.timeOfDayMinutes?.let { formatTimeOfDay(it) } ?: ""
    return when (item.scheduleKind) {
        ScheduleKind.Daily -> stringResource(R.string.sd_schedule_daily, time)
        ScheduleKind.Weekly ->
            stringResource(R.string.sd_schedule_weekly, weekdaysSummary(item.weekdayMask ?: 0), time)
        ScheduleKind.Monthly ->
            stringResource(R.string.sd_schedule_monthly, item.dayOfMonth ?: 1, time)
        ScheduleKind.OneTime ->
            stringResource(R.string.sd_schedule_one_time, formatIsraelDateTime(item.nextRunAtUtc))
    }
}

/**
 * Comma-joined Hebrew weekday abbreviations for the bits set in [mask]
 * (bit 0 = Sunday … bit 6 = Saturday, E4.3.5). Empty string for an empty mask.
 */
@Composable
fun weekdaysSummary(mask: Int): String {
    val abbreviations = stringArrayResource(R.array.sd_weekday_abbreviations)
    return (0..6)
        .filter { (mask and (1 shl it)) != 0 }
        .joinToString(separator = ", ") { abbreviations[it] }
}
