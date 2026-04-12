namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Utility methods for working with Israel time (<c>Asia/Jerusalem</c>) as required by E8.3.
/// <para>
/// Scheduled deposit schedules are authored and interpreted in Israel time. All timestamps
/// in the database are stored as UTC. This helper bridges the two representations.
/// </para>
/// <para>
/// On Windows the system time zone ID is <c>"Israel Standard Time"</c>. The IANA ID
/// <c>"Asia/Jerusalem"</c> is supported on .NET 8+ via <see cref="TimeZoneInfo.FindSystemTimeZoneById"/>.
/// We try the IANA name first for cross-platform compatibility.
/// </para>
/// </summary>
public static class IsraelTimeHelper
{
    private static readonly Lazy<TimeZoneInfo> _israelTz = new(FindIsraelTimeZone);

    /// <summary>
    /// Returns the <see cref="TimeZoneInfo"/> for Israel (<c>Asia/Jerusalem</c>).
    /// </summary>
    public static TimeZoneInfo GetIsraelTimeZone() => _israelTz.Value;

    /// <summary>
    /// Converts a UTC <see cref="DateTime"/> to Israel local time.
    /// </summary>
    /// <param name="utc">A UTC datetime (its <see cref="DateTime.Kind"/> should be <see cref="DateTimeKind.Utc"/>).</param>
    /// <returns>The equivalent datetime in Israel time with <see cref="DateTimeKind.Unspecified"/>.</returns>
    public static DateTime ToIsraelTime(DateTime utc)
    {
        return TimeZoneInfo.ConvertTimeFromUtc(
            DateTime.SpecifyKind(utc, DateTimeKind.Utc),
            GetIsraelTimeZone());
    }

    /// <summary>
    /// Converts a <c>time_of_day_minutes</c> value (0–1439) to a <see cref="TimeSpan"/> for display.
    /// </summary>
    /// <param name="minutes">Minutes since midnight (e.g., 570 = 09:30).</param>
    /// <returns>A <see cref="TimeSpan"/> representing the time of day.</returns>
    public static TimeSpan MinutesToTimeSpan(int minutes)
    {
        return TimeSpan.FromMinutes(minutes);
    }

    /// <summary>
    /// Converts a user-input Israel-local datetime to UTC for the <c>p_next_run_at_utc</c> parameter
    /// when creating a OneTime scheduled deposit.
    /// </summary>
    /// <param name="israelLocal">
    /// A datetime representing Israel local time (Kind should be <see cref="DateTimeKind.Unspecified"/>).
    /// </param>
    /// <returns>The equivalent UTC datetime.</returns>
    public static DateTime OneTimeUtcFromIsraelDateTime(DateTime israelLocal)
    {
        return TimeZoneInfo.ConvertTimeToUtc(
            DateTime.SpecifyKind(israelLocal, DateTimeKind.Unspecified),
            GetIsraelTimeZone());
    }

    /// <summary>
    /// Formats <c>time_of_day_minutes</c> as an "HH:mm" string for display.
    /// </summary>
    public static string FormatTimeOfDay(int minutes)
    {
        var ts = MinutesToTimeSpan(minutes);
        return $"{(int)ts.TotalHours:D2}:{ts.Minutes:D2}";
    }

    private static TimeZoneInfo FindIsraelTimeZone()
    {
        // .NET 8 on Windows supports both IANA and Windows IDs.
        // Try IANA first for cross-platform compatibility (future Kotlin parity per E8.12).
        try
        {
            return TimeZoneInfo.FindSystemTimeZoneById("Asia/Jerusalem");
        }
        catch (TimeZoneNotFoundException)
        {
            return TimeZoneInfo.FindSystemTimeZoneById("Israel Standard Time");
        }
    }
}
