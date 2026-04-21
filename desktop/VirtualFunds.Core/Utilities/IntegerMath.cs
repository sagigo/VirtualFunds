namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Pure-integer arithmetic helpers for deterministic cross-platform money math (E6.4).
/// <para>
/// These operations avoid <c>decimal</c> / <c>double</c> entirely so that C# and Kotlin
/// implementations produce bit-identical results.
/// </para>
/// </summary>
public static class IntegerMath
{
    /// <summary>
    /// Computes <c>round(numerator / denominator)</c> using banker's rounding (round half to even),
    /// implemented entirely with integer arithmetic per the E6.4 spec.
    /// </summary>
    /// <remarks>
    /// <para>
    /// The numerator is <see cref="Int128"/> so callers can pass a product of two <c>long</c> values
    /// without overflow: <c>BankersRound((Int128)a * b, denominator)</c>.
    /// </para>
    /// <para><b>Algorithm (E6.4):</b></para>
    /// <list type="number">
    ///   <item>Extract sign; work with absolute values.</item>
    ///   <item><c>q = abs(numerator) / denominator</c>, <c>r = abs(numerator) % denominator</c>.</item>
    ///   <item>If <c>2*r &lt; denominator</c> → round down (<c>q</c>).</item>
    ///   <item>If <c>2*r &gt; denominator</c> → round up (<c>q + 1</c>).</item>
    ///   <item>If <c>2*r == denominator</c> (exact midpoint) → <c>q</c> if even, <c>q + 1</c> if odd.</item>
    ///   <item>Reapply the original sign.</item>
    /// </list>
    /// </remarks>
    /// <param name="numerator">
    /// The dividend as <see cref="Int128"/>. May be negative. Use <c>(Int128)a * b</c> to avoid
    /// <c>long</c> overflow when the numerator is a product of two large values.
    /// </param>
    /// <param name="denominator">The divisor. Must be positive.</param>
    /// <returns>The rounded quotient as a <c>long</c>.</returns>
    /// <exception cref="ArgumentOutOfRangeException">
    /// Thrown when <paramref name="denominator"/> is zero or negative.
    /// </exception>
    public static long BankersRound(Int128 numerator, long denominator)
    {
        if (denominator <= 0)
            throw new ArgumentOutOfRangeException(nameof(denominator), denominator,
                "Denominator must be positive.");

        // Step 1: Extract sign and work with absolute values.
        var sign = numerator >= 0 ? 1 : -1;
        var absNumerator = Int128.Abs(numerator);

        // Step 2: Integer division and remainder.
        var q = absNumerator / denominator;
        var r = absNumerator % denominator;

        // Step 3–4: Compare 2*r against denominator to decide rounding direction.
        var twoR = 2 * r;

        Int128 roundedAbsolute;
        if (twoR < denominator)
        {
            // Fractional part < 0.5 → round down.
            roundedAbsolute = q;
        }
        else if (twoR > denominator)
        {
            // Fractional part > 0.5 → round up.
            roundedAbsolute = q + 1;
        }
        else
        {
            // Exact midpoint (fractional part == 0.5) → round to even.
            roundedAbsolute = (q % 2 == 0) ? q : q + 1;
        }

        // Step 5: Reapply sign.
        return (long)(sign * roundedAbsolute);
    }
}
