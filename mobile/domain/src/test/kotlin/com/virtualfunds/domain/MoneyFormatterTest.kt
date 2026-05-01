package com.virtualfunds.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for formatAgoras — verifies the output matches the shared format rule:
 * "1,234.56 ₪" (comma thousands separator, two decimal places, ₪ after the number).
 *
 * These tests also serve as cross-platform parity anchors: the desktop MoneyFormatterTests.cs
 * tests the same cases and must produce identical strings (E10.4).
 *
 * FunSpec is a Kotest spec style where each test is a plain function call:
 *   test("description") { ... }
 * It reads like documentation and maps naturally to xUnit Facts.
 */
class MoneyFormatterTest : FunSpec({

    test("zero agoras formats as 0.00") {
        formatAgoras(0L) shouldBe "0.00 ₪"
    }

    test("whole NIS amount with no agoras") {
        formatAgoras(100L) shouldBe "1.00 ₪"
    }

    test("agoras below 10 are zero-padded") {
        formatAgoras(105L) shouldBe "1.05 ₪"
    }

    test("typical amount with thousands separator") {
        formatAgoras(123456L) shouldBe "1,234.56 ₪"
    }

    test("large amount with multiple comma groups") {
        formatAgoras(1_000_000_00L) shouldBe "1,000,000.00 ₪"
    }

    test("negative amount gets leading minus sign") {
        formatAgoras(-5000L) shouldBe "-50.00 ₪"
    }

    test("negative amount below 1 NIS") {
        formatAgoras(-7L) shouldBe "-0.07 ₪"
    }

    test("exactly 99 agoras (no whole NIS)") {
        formatAgoras(99L) shouldBe "0.99 ₪"
    }

    test("exactly 1 agora") {
        formatAgoras(1L) shouldBe "0.01 ₪"
    }
})
