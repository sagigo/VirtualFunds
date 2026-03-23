using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="MoneyFormatter.FormatAgoras"/> (CLAUDE.md money display spec).
/// </summary>
public class MoneyFormatterTests
{
    [Theory]
    [InlineData(0, "0.00 ₪")]
    [InlineData(100, "1.00 ₪")]
    [InlineData(123456, "1,234.56 ₪")]
    [InlineData(50, "0.50 ₪")]
    [InlineData(5, "0.05 ₪")]
    [InlineData(1000000, "10,000.00 ₪")]
    [InlineData(-500, "-5.00 ₪")]
    [InlineData(-123456, "-1,234.56 ₪")]
    public void FormatAgoras_FormatsCorrectly(long agoras, string expected)
    {
        Assert.Equal(expected, MoneyFormatter.FormatAgoras(agoras));
    }
}
