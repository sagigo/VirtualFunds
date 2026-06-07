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

    // --- Desktop parity anchors (E10.4) ---
    // Each test below mirrors one row of IntegerMathTests.cs InlineData. The expected
    // values are taken verbatim from the desktop tests so a regression in either
    // implementation surfaces as a failing parity test on this side.

    test("parity 95/10 → 10 (tie at 9.5, 9 is odd → up)") {
        bankersRound(95L, 10L) shouldBe 10L
    }

    test("parity 85/10 → 8 (tie at 8.5, 8 is even → stay)") {
        bankersRound(85L, 10L) shouldBe 8L
    }

    test("parity 25/10 → 2 (tie at 2.5, 2 is even → stay)") {
        bankersRound(25L, 10L) shouldBe 2L
    }

    test("parity -25/10 → -2 (tie at -2.5, 2 is even → stay; sign reapplied)") {
        bankersRound(-25L, 10L) shouldBe -2L
    }

    test("parity 94/10 → 9 (below midpoint)") {
        bankersRound(94L, 10L) shouldBe 9L
    }

    test("parity 99/10 → 10 (above midpoint)") {
        bankersRound(99L, 10L) shouldBe 10L
    }

    // E6.11 proportional-scaling scenarios — these are the actual rounding inputs the
    // revaluation algorithm computes inside SupabaseFundRepository.revaluePortfolio.

    test("E6.11 scaling: 3 funds × 3333, new total 10000 → each rounds to 3333") {
        // Each fund's share: 10000 * 3333 / 9999 = 3333.3333... → 3333.
        // (Remainder distribution is the repo's job, not this function's.)
        val newTotal = 10_000L
        val share = 3_333L
        val oldTotal = 9_999L
        bankersRound(newTotal.toBigInteger() * share.toBigInteger(), oldTotal) shouldBe 3_333L
    }

    test("E6.11 scaling: tie case rounds to even (50.5 with q=50 → 50)") {
        // 2 funds: 50 and 50, old total 100, new total 101.
        // Fund share: 101 * 50 / 100 = 50.5  → tie, q=50 (even) → 50.
        bankersRound(101L.toBigInteger() * 50L.toBigInteger(), 100L) shouldBe 50L
    }

    test("E6.11 scaling: large values via BigInteger numerator do not overflow") {
        // 10 billion shekels each side. newTotal * balance would overflow Long.
        val newTotal = 1_000_000_000_000L          // 10 billion NIS in agoras
        val balance = 500_000_000_000L
        val oldTotal = 1_000_000_000_000L
        bankersRound(
            newTotal.toBigInteger() * balance.toBigInteger(),
            oldTotal,
        ) shouldBe 500_000_000_000L
    }
})
