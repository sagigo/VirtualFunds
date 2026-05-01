package com.virtualfunds.domain

import java.math.BigInteger

/**
 * Computes round(numerator / denominator) using banker's rounding (round half to even).
 * Pure-integer implementation per E6.4.
 *
 * Both C# (IntegerMath.cs) and this Kotlin implementation follow the identical algorithm
 * to guarantee bit-identical results across platforms (E10.4).
 *
 * Algorithm (E6.4):
 *   1. Extract sign; work with absolute values.
 *   2. q = abs(numerator) / denominator,  r = abs(numerator) % denominator
 *   3. if 2*r < denominator  → round down (q)
 *   4. if 2*r > denominator  → round up (q + 1)
 *   5. if 2*r == denominator → q if q is even, q+1 if q is odd  (round to even)
 *   6. Reapply original sign.
 *
 * The numerator accepts BigInteger so callers can pass products of two Longs without
 * overflow: bankersRound(newTotal.toBigInteger() * oldBalance.toBigInteger(), oldTotal).
 * This mirrors the Int128 numerator in the C# version.
 *
 * @param numerator  The dividend. May be negative.
 * @param denominator The divisor. Must be positive.
 */
fun bankersRound(numerator: BigInteger, denominator: Long): Long {
    require(denominator > 0) { "Denominator must be positive" }

    val zero = BigInteger.ZERO
    val one = BigInteger.ONE
    val two = BigInteger.valueOf(2)
    val denom = denominator.toBigInteger()

    val sign = if (numerator >= zero) 1L else -1L
    val absNumerator = numerator.abs()

    val q = absNumerator / denom
    val r = absNumerator % denom
    val twoR = r * two

    val roundedAbsolute = when {
        twoR < denom -> q                                          // round down
        twoR > denom -> q + one                                    // round up
        else -> if (q % two == zero) q else q + one               // midpoint → round to even
    }

    return sign * roundedAbsolute.toLong()
}

/** Convenience overload for cases where the numerator fits in a Long without overflow. */
fun bankersRound(numerator: Long, denominator: Long): Long =
    bankersRound(numerator.toBigInteger(), denominator)
