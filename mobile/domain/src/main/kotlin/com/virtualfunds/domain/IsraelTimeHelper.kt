package com.virtualfunds.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Israel timezone for all schedule computations (E8.3, E8.12).
 *
 * The spec mandates "Asia/Jerusalem" (IANA ID). This is available natively on minSdk 31
 * via java.time — no third-party library or desugaring is needed.
 *
 * Mirrors IsraelTimeHelper.cs in VirtualFunds.Core.
 */
val ISRAEL_ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

/** Converts a UTC [Instant] to a [ZonedDateTime] in Israel time. */
fun Instant.toIsraelTime(): ZonedDateTime = atZone(ISRAEL_ZONE)

/**
 * Formats a time_of_day_minutes value (0–1439) as "HH:mm" for display.
 * E.g. 570 → "09:30".
 */
fun formatTimeOfDay(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "%02d:%02d".format(hours, mins)
}
