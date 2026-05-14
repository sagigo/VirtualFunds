package com.virtualfunds.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [ScheduleKind] wire-value round-tripping (E8.2).
 *
 * Wire values must match the SQL CHECK constraint:
 *   schedule_kind IN ('Daily', 'Weekly', 'Monthly', 'OneTime')
 * and must match the desktop's ScheduleKind.cs string values (E10.4).
 */
class ScheduleKindTest : FunSpec({

    // ── Wire values ──────────────────────────────────────────────────────────

    test("OneTime wire value is 'OneTime'") {
        ScheduleKind.OneTime.wireValue shouldBe "OneTime"
    }

    test("Daily wire value is 'Daily'") {
        ScheduleKind.Daily.wireValue shouldBe "Daily"
    }

    test("Weekly wire value is 'Weekly'") {
        ScheduleKind.Weekly.wireValue shouldBe "Weekly"
    }

    test("Monthly wire value is 'Monthly'") {
        ScheduleKind.Monthly.wireValue shouldBe "Monthly"
    }

    // ── fromWire round-trips ─────────────────────────────────────────────────

    test("every entry round-trips through fromWire") {
        ScheduleKind.entries.forEach { kind ->
            ScheduleKind.fromWire(kind.wireValue) shouldBe kind
        }
    }

    test("unknown wire value returns null") {
        ScheduleKind.fromWire("Biweekly") shouldBe null
    }

    test("empty string returns null") {
        ScheduleKind.fromWire("") shouldBe null
    }

    test("case-sensitive: lowercase does not match") {
        ScheduleKind.fromWire("daily") shouldBe null
        ScheduleKind.fromWire("monthly") shouldBe null
    }

    test("wire values are unique (no silent duplicates)") {
        val distinct = ScheduleKind.entries.map { it.wireValue }.toSet()
        distinct.size shouldBe ScheduleKind.entries.size
    }
})
