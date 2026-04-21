using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="IntegerMath.BankersRound"/> — the pure-integer banker's rounding
/// algorithm specified in E6.4.
/// </summary>
public class IntegerMathTests
{
    // -------------------------------------------------------------------------------------
    // Basic rounding (no tie-break)
    // -------------------------------------------------------------------------------------

    [Theory]
    [InlineData(7, 2, 4)]           // 3.5 → 4 (tie, 3 is odd → round up)
    [InlineData(5, 2, 2)]           // 2.5 → 2 (tie, 2 is even → stay)
    [InlineData(3, 2, 2)]           // 1.5 → 2 (tie, 1 is odd → round up)
    [InlineData(1, 2, 0)]           // 0.5 → 0 (tie, 0 is even → stay)
    public void BankersRound_TieBreak_RoundsToEven(long numerator, long denominator, long expected)
    {
        Assert.Equal(expected, IntegerMath.BankersRound(numerator, denominator));
    }

    [Theory]
    [InlineData(10, 3, 3)]          // 3.333... → 3 (below midpoint)
    [InlineData(11, 3, 4)]          // 3.666... → 4 (above midpoint)
    [InlineData(0, 5, 0)]           // 0 / 5 = 0
    [InlineData(1, 1, 1)]           // 1 / 1 = 1 (exact)
    [InlineData(100, 10, 10)]       // exact division
    [InlineData(99, 10, 10)]        // 9.9 → 10 (above midpoint)
    [InlineData(94, 10, 9)]         // 9.4 → 9 (below midpoint)
    [InlineData(95, 10, 10)]        // 9.5 → 10 (tie, 9 is odd → round up)
    [InlineData(85, 10, 8)]         // 8.5 → 8 (tie, 8 is even → stay)
    public void BankersRound_BasicCases(long numerator, long denominator, long expected)
    {
        Assert.Equal(expected, IntegerMath.BankersRound(numerator, denominator));
    }

    // -------------------------------------------------------------------------------------
    // Negative numerator — sign handling per E6.4 steps 1 & 5
    // -------------------------------------------------------------------------------------

    [Theory]
    [InlineData(-7, 2, -4)]         // -3.5 → -4 (tie, 3 is odd → round away from zero, then negate)
    [InlineData(-5, 2, -2)]         // -2.5 → -2 (tie, 2 is even → stay)
    [InlineData(-10, 3, -3)]        // -3.333... → -3
    [InlineData(-11, 3, -4)]        // -3.666... → -4
    [InlineData(-1, 2, 0)]          // -0.5 → 0 (tie, 0 is even → stay)
    [InlineData(-100, 10, -10)]     // exact
    public void BankersRound_NegativeNumerator(long numerator, long denominator, long expected)
    {
        Assert.Equal(expected, IntegerMath.BankersRound(numerator, denominator));
    }

    // -------------------------------------------------------------------------------------
    // Proportional scaling scenario — the actual use case in RevaluePortfolioAsync
    // -------------------------------------------------------------------------------------

    [Fact]
    public void BankersRound_ProportionalScaling_ThreeFunds()
    {
        // Portfolio: 3 funds each with 3333 agoras (total 9999).
        // New total: 10000. Each fund's share: 10000 * 3333 / 9999 = 3333.3333...
        // Rounds down to 3333 for each → provisional sum 9999, remainder +1.
        // (Remainder distribution is not this method's job — just verifying rounding.)
        long newTotal = 10000;
        long oldTotal = 9999;

        Assert.Equal(3333, IntegerMath.BankersRound((Int128)newTotal * 3333, oldTotal));
    }

    [Fact]
    public void BankersRound_ProportionalScaling_TieCase()
    {
        // 2 funds: 50 and 50, old total 100, new total 101.
        // Fund share: 101 * 50 / 100 = 5050 / 100 = 50.5
        // Tie → 50 is even → rounds to 50.
        Assert.Equal(50, IntegerMath.BankersRound((Int128)101 * 50, 100));
    }

    // -------------------------------------------------------------------------------------
    // Large values — verifies Int128 prevents overflow
    // -------------------------------------------------------------------------------------

    [Fact]
    public void BankersRound_LargeValues_NoOverflow()
    {
        // 10 billion NIS = 1,000,000,000,000 agoras (1 trillion).
        // numerator = newTotal * balance = 1T * 500B = 5 * 10^23, which overflows long.
        long newTotal = 1_000_000_000_000;
        long balance = 500_000_000_000;
        long oldTotal = 1_000_000_000_000;

        // Expected: newTotal * balance / oldTotal = 500_000_000_000 (exact).
        // Cast to Int128 before multiplication to avoid long overflow.
        Assert.Equal(500_000_000_000, IntegerMath.BankersRound((Int128)newTotal * balance, oldTotal));
    }

    [Fact]
    public void BankersRound_LargeValues_WithRounding()
    {
        // Use pre-computed numerator to avoid long overflow in the test itself.
        // numerator = 333_333_333_333, denominator = 100 → 3_333_333_333.33 → rounds to 3_333_333_333
        Assert.Equal(3_333_333_333, IntegerMath.BankersRound(333_333_333_333, 100));
    }

    // -------------------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------------------

    [Fact]
    public void BankersRound_ZeroNumerator_ReturnsZero()
    {
        Assert.Equal(0, IntegerMath.BankersRound(0, 42));
    }

    [Fact]
    public void BankersRound_DenominatorOne_ReturnsNumerator()
    {
        Assert.Equal(12345, IntegerMath.BankersRound(12345, 1));
        Assert.Equal(-99, IntegerMath.BankersRound(-99, 1));
    }

    [Fact]
    public void BankersRound_ZeroDenominator_Throws()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => IntegerMath.BankersRound(10, 0));
    }

    [Fact]
    public void BankersRound_NegativeDenominator_Throws()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => IntegerMath.BankersRound(10, -5));
    }

    // -------------------------------------------------------------------------------------
    // Cross-check with decimal Math.Round for non-overflowing values
    // -------------------------------------------------------------------------------------

    [Theory]
    [InlineData(1, 4)]
    [InlineData(3, 4)]
    [InlineData(15, 10)]
    [InlineData(25, 10)]
    [InlineData(35, 10)]
    [InlineData(45, 10)]
    [InlineData(123, 7)]
    [InlineData(999, 13)]
    [InlineData(-15, 10)]
    [InlineData(-25, 10)]
    public void BankersRound_MatchesDecimalMathRound(long numerator, long denominator)
    {
        var expected = (long)Math.Round(
            (decimal)numerator / denominator, 0, MidpointRounding.ToEven);

        Assert.Equal(expected, IntegerMath.BankersRound(numerator, denominator));
    }
}
