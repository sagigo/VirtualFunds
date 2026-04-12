using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="IsraelTimeHelper"/> (E8.3, E8.12).
/// </summary>
public class IsraelTimeHelperTests
{
    [Fact]
    public void GetIsraelTimeZone_ReturnsNonNull()
    {
        var tz = IsraelTimeHelper.GetIsraelTimeZone();
        Assert.NotNull(tz);
    }

    [Theory]
    [InlineData(0, 0, 0)]       // midnight
    [InlineData(570, 9, 30)]    // 09:30
    [InlineData(1439, 23, 59)]  // 23:59
    [InlineData(60, 1, 0)]      // 01:00
    [InlineData(750, 12, 30)]   // 12:30
    public void MinutesToTimeSpan_ReturnsCorrectTime(int minutes, int expectedHours, int expectedMinutes)
    {
        var ts = IsraelTimeHelper.MinutesToTimeSpan(minutes);
        Assert.Equal(expectedHours, (int)ts.TotalHours);
        Assert.Equal(expectedMinutes, ts.Minutes);
    }

    [Theory]
    [InlineData(0, "00:00")]
    [InlineData(570, "09:30")]
    [InlineData(1439, "23:59")]
    [InlineData(60, "01:00")]
    public void FormatTimeOfDay_ReturnsCorrectString(int minutes, string expected)
    {
        Assert.Equal(expected, IsraelTimeHelper.FormatTimeOfDay(minutes));
    }

    [Fact]
    public void OneTimeUtcFromIsraelDateTime_RoundTrips()
    {
        // Pick a specific Israel-local time.
        var israelLocal = new DateTime(2026, 4, 15, 9, 0, 0, DateTimeKind.Unspecified);

        // Convert to UTC.
        var utc = IsraelTimeHelper.OneTimeUtcFromIsraelDateTime(israelLocal);

        // Convert back to Israel time.
        var roundTripped = IsraelTimeHelper.ToIsraelTime(utc);

        Assert.Equal(israelLocal.Year, roundTripped.Year);
        Assert.Equal(israelLocal.Month, roundTripped.Month);
        Assert.Equal(israelLocal.Day, roundTripped.Day);
        Assert.Equal(israelLocal.Hour, roundTripped.Hour);
        Assert.Equal(israelLocal.Minute, roundTripped.Minute);
    }

    [Fact]
    public void ToIsraelTime_ConvertsFromUtc()
    {
        // Israel is UTC+2 in winter, UTC+3 in summer.
        // April 15 2026 is during daylight saving (IDT = UTC+3).
        var utc = new DateTime(2026, 4, 15, 6, 0, 0, DateTimeKind.Utc);
        var israel = IsraelTimeHelper.ToIsraelTime(utc);

        // 06:00 UTC = 09:00 Israel (IDT, UTC+3).
        Assert.Equal(9, israel.Hour);
        Assert.Equal(0, israel.Minute);
    }
}
