package com.virtualfunds.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Tests for [IsraelTimeHelper] (E8.3, E8.12).
 *
 * Israel uses "Asia/Jerusalem" (IANA), which is UTC+2 in winter (IST) and UTC+3 in summer
 * (IDT). Some tests pin the UTC instant to a known season to verify the offset is applied.
 * These cases also serve as cross-platform parity anchors — the desktop's
 * IsraelTimeHelperTests.cs uses the same instants.
 */
class IsraelTimeHelperTest : FunSpec({

    // ── formatTimeOfDay ──────────────────────────────────────────────────────

    test("midnight is 00:00") {
        formatTimeOfDay(0) shouldBe "00:00"
    }

    test("one minute past midnight is 00:01") {
        formatTimeOfDay(1) shouldBe "00:01"
    }

    test("9:30 AM → 570 minutes") {
        formatTimeOfDay(570) shouldBe "09:30"
    }

    test("12:00 noon → 720 minutes") {
        formatTimeOfDay(720) shouldBe "12:00"
    }

    test("13:05 (lunch hour) is zero-padded minutes") {
        formatTimeOfDay(785) shouldBe "13:05"
    }

    test("23:59 is the last valid time-of-day") {
        formatTimeOfDay(1439) shouldBe "23:59"
    }

    test("single-digit hours are zero-padded") {
        // 8:00 = 480 minutes — checks that hours < 10 get the leading zero.
        formatTimeOfDay(480) shouldBe "08:00"
    }

    // ── toIsraelTime ─────────────────────────────────────────────────────────

    test("UTC instant in January (IST = UTC+2) converts correctly") {
        // 2024-01-15 10:00:00 UTC should be 12:00 Israel time (UTC+2, winter/standard time).
        val utc = Instant.parse("2024-01-15T10:00:00Z")
        val israelTime: ZonedDateTime = utc.toIsraelTime()
        israelTime.hour shouldBe 12
        israelTime.minute shouldBe 0
        israelTime.zone shouldBe ISRAEL_ZONE
    }

    test("UTC instant in July (IDT = UTC+3) converts correctly") {
        // 2024-07-15 10:00:00 UTC should be 13:00 Israel time (UTC+3, summer/daylight time).
        val utc = Instant.parse("2024-07-15T10:00:00Z")
        val israelTime: ZonedDateTime = utc.toIsraelTime()
        israelTime.hour shouldBe 13
        israelTime.minute shouldBe 0
        israelTime.zone shouldBe ISRAEL_ZONE
    }

    test("date component is preserved across the timezone conversion") {
        // 2024-03-10 22:30:00 UTC → 2024-03-11 00:30:00 IST (midnight rollover, UTC+2)
        val utc = Instant.parse("2024-03-10T22:30:00Z")
        val israelTime = utc.toIsraelTime()
        israelTime.year shouldBe 2024
        israelTime.monthValue shouldBe 3
        israelTime.dayOfMonth shouldBe 11
        israelTime.hour shouldBe 0
        israelTime.minute shouldBe 30
    }

    // ── israelDateTimeToUtc (OneTime conversion, E8.12) ──────────────────────

    test("OneTime date+time in January (IST = UTC+2) converts to UTC") {
        // 09:00 (540 min) on 2026-01-15 Israel time → 07:00 UTC (winter, UTC+2).
        val utc = israelDateTimeToUtc(LocalDate.of(2026, 1, 15), 9 * 60)
        utc shouldBe Instant.parse("2026-01-15T07:00:00Z")
    }

    test("OneTime date+time in July (IDT = UTC+3) converts to UTC") {
        // 09:00 (540 min) on 2026-07-15 Israel time → 06:00 UTC (summer, UTC+3).
        val utc = israelDateTimeToUtc(LocalDate.of(2026, 7, 15), 9 * 60)
        utc shouldBe Instant.parse("2026-07-15T06:00:00Z")
    }

    test("OneTime conversion round-trips back to the same Israel wall-clock") {
        val date = LocalDate.of(2026, 5, 20)
        val minutes = 14 * 60 + 30 // 14:30
        val israel = israelDateTimeToUtc(date, minutes).toIsraelTime()
        israel.toLocalDate() shouldBe date
        israel.hour shouldBe 14
        israel.minute shouldBe 30
    }

    // ── formatIsraelDateTime ─────────────────────────────────────────────────

    test("formatIsraelDateTime renders dd/MM/yyyy HH:mm in Israel time") {
        // 2026-01-15 07:00 UTC → 09:00 Israel (UTC+2).
        formatIsraelDateTime(Instant.parse("2026-01-15T07:00:00Z")) shouldBe "15/01/2026 09:00"
    }
})
