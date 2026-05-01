package com.virtualfunds.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigInteger

/**
 * Unit tests for bankersRound — verifies E6.4 (deterministic banker's rounding).
 *
 * These tests are cross-platform parity anchors: IntegerMathTests.cs on the desktop
 * covers the same cases and must produce identical results (E10.4).
 */
class IntegerMathTest : FunSpec({

    // --- Basic rounding cases ---

    test("exact integer division rounds exactly") {
        bankersRound(10L, 2L) shouldBe 5L
    }

    test("fractional part below 0.5 rounds down") {
        // 1/3 = 0.333... → 0
        bankersRound(1L, 3L) shouldBe 0L
    }

    test("fractional part above 0.5 rounds up") {
        // 2/3 = 0.666... → 1
        bankersRound(2L, 3L) shouldBe 1L
    }

    // --- Midpoint (banker's rounding) cases ---

    test("midpoint with even quotient rounds down (stays even)") {
        // 1/2 = 0.5, q=0 (even) → 0
        bankersRound(1L, 2L) shouldBe 0L
    }

    test("midpoint with odd quotient rounds up (to even)") {
        // 3/2 = 1.5, q=1 (odd) → 2
        bankersRound(3L, 2L) shouldBe 2L
    }

    test("midpoint 5/2 = 2.5, q=2 (even) rounds down to 2") {
        bankersRound(5L, 2L) shouldBe 2L
    }

    test("midpoint 7/2 = 3.5, q=3 (odd) rounds up to 4") {
        bankersRound(7L, 2L) shouldBe 4L
    }

    // --- Negative numerator cases ---

    test("negative value with fractional part below 0.5 rounds toward zero") {
        // -1/3 = -0.333... → 0
        bankersRound(-1L, 3L) shouldBe 0L
    }

    test("negative value with fractional part above 0.5 rounds away from zero") {
        // -2/3 = -0.666... → -1
        bankersRound(-2L, 3L) shouldBe -1L
    }

    test("negative midpoint with even quotient rounds toward zero") {
        // -1/2 = -0.5, q=0 (even) → 0
        bankersRound(-1L, 2L) shouldBe 0L
    }

    test("negative midpoint with odd quotient rounds away from zero") {
        // -3/2 = -1.5, q=1 (odd) → -2
        bankersRound(-3L, 2L) shouldBe -2L
    }

    // --- Overflow-safe BigInteger overload ---

    test("BigInteger overload matches Long overload for small values") {
        bankersRound(BigInteger.valueOf(3), 2L) shouldBe bankersRound(3L, 2L)
    }

    test("BigInteger overload handles product of two large Longs without overflow") {
        // Simulates new_total * old_balance / old_total in a revaluation.
        // 9_000_000_000L * 8_000_000_000L would overflow Long — BigInteger handles it.
        val newTotal = 9_000_000_000L
        val oldBalance = 8_000_000_000L
        val oldTotal = 10_000_000_000L
        val numerator = newTotal.toBigInteger() * oldBalance.toBigInteger()
        bankersRound(numerator, oldTotal) shouldBe 7_200_000_000L
    }

    // --- Error case ---

    test("zero denominator throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            bankersRound(1L, 0L)
        }
    }

    test("negative denominator throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            bankersRound(1L, -1L)
        }
    }
})
