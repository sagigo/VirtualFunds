package com.virtualfunds.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Israel timezone for all schedule computations (E8.3, E8.12).
 *
 * The spec mandates "Asia/Jerusalem" (IANA ID). This is available natively on minSdk 31
 * via java.time — no third-party library or desugaring is needed.
 *
 * Mirrors IsraelTimeHelper.cs in VirtualFunds.Core.
 */
val ISRAEL_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

/** "dd/MM/yyyy HH:mm" — matches the desktop `ScheduledDepositListItem.FormattedNextRun`. */
private val ISRAEL_DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** Converts a UTC [Instant] to a [ZonedDateTime] in Israel time. */
fun Instant.toIsraelTime(): ZonedDateTime = atZone(ISRAEL_ZONE)

/**
 * Formats a UTC [Instant] as a "dd/MM/yyyy HH:mm" string in Israel time, for displaying
 * a scheduled deposit's next-run time (mirrors desktop `ScheduledDepositListItem`).
 */
fun formatIsraelDateTime(instant: Instant): String =
    instant.toIsraelTime().format(ISRAEL_DATE_TIME_FORMAT)

/**
 * Formats a time_of_day_minutes value (0–1439) as "HH:mm" for display.
 * E.g. 570 → "09:30".
 */
fun formatTimeOfDay(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    // Locale.ROOT keeps digits Western (ASCII) regardless of the device locale, matching
    // formatAgoras and the project's locale-independent number-formatting rule.
    return String.format(Locale.ROOT, "%02d:%02d", hours, mins)
}

/**
 * Converts a OneTime scheduled deposit's user-picked Israel wall-clock date + time to the
 * UTC [Instant] stored in `next_run_at_utc` (E8.12 OneTime: "the user-specified single
 * execution time, converted to UTC"). Mirrors desktop `IsraelTimeHelper.OneTimeUtcFromIsraelDateTime`.
 *
 * @param date the calendar day the user picked, interpreted in Israel time.
 * @param minutesOfDay minutes since midnight Israel time (0–1439).
 *
 * Daylight-saving handling: combining the wall-clock date+time with [ISRAEL_ZONE] via
 * [java.time.LocalDateTime.atZone] resolves the spring-forward gap by moving forward and the
 * autumn-back overlap to the earlier offset — i.e. it interprets the value as the local clock
 * time the user sees, exactly as the spec intends (never a fixed UTC offset).
 */
fun israelDateTimeToUtc(date: LocalDate, minutesOfDay: Int): Instant {
    val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
    return date.atTime(time).atZone(ISRAEL_ZONE).toInstant()
}
