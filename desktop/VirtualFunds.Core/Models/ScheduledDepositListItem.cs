using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Display-oriented model for the scheduled deposits list (PR-8).
/// Combines scheduled deposit data with computed display properties and a resolved fund name.
/// </summary>
public class ScheduledDepositListItem
{
    // ---- Hebrew day abbreviations (Sunday=0 … Saturday=6) for weekday mask display ----
    private static readonly string[] HebrewDayAbbreviations =
        ["א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"];

    // ---- Hebrew schedule kind labels ----
    private static readonly Dictionary<ScheduleKind, string> ScheduleKindLabels = new()
    {
        [ScheduleKind.OneTime] = "חד פעמי",
        [ScheduleKind.Daily]   = "יומי",
        [ScheduleKind.Weekly]  = "שבועי",
        [ScheduleKind.Monthly] = "חודשי",
    };

    /// <summary>Unique identifier of the scheduled deposit.</summary>
    public Guid ScheduledDepositId { get; init; }

    /// <summary>The portfolio this deposit belongs to.</summary>
    public Guid PortfolioId { get; init; }

    /// <summary>The target fund ID.</summary>
    public Guid FundId { get; init; }

    /// <summary>User-visible name of the scheduled deposit.</summary>
    public string Name { get; init; } = string.Empty;

    /// <summary>Optional note.</summary>
    public string? Note { get; init; }

    /// <summary>Whether the scheduled deposit is active.</summary>
    public bool IsEnabled { get; init; }

    /// <summary>Amount to deposit per execution, in agoras.</summary>
    public long AmountAgoras { get; init; }

    /// <summary>The schedule kind (E8.2).</summary>
    public ScheduleKind ScheduleKind { get; init; }

    /// <summary>Minutes since midnight in Israel time (0–1439), or null for OneTime.</summary>
    public int? TimeOfDayMinutes { get; init; }

    /// <summary>Weekday bitmask for Weekly schedules, or null.</summary>
    public int? WeekdayMask { get; init; }

    /// <summary>Day of month for Monthly schedules (1–28), or null.</summary>
    public int? DayOfMonth { get; init; }

    /// <summary>Next scheduled execution time in UTC.</summary>
    public DateTime NextRunAtUtc { get; init; }

    /// <summary>Resolved display name of the target fund.</summary>
    public string FundName { get; init; } = string.Empty;

    /// <summary>Formatted deposit amount (e.g. "1,234.56 ₪").</summary>
    public string FormattedAmount => MoneyFormatter.FormatAgoras(AmountAgoras);

    /// <summary>
    /// Hebrew status label: "מופעל" (enabled) or "מושבת" (disabled).
    /// </summary>
    public string StatusLabel => IsEnabled ? "מופעל" : "מושבת";

    /// <summary>
    /// Next run time formatted in Israel local time (e.g. "06/04/2026 09:00").
    /// </summary>
    public string FormattedNextRun
    {
        get
        {
            var israelTime = IsraelTimeHelper.ToIsraelTime(NextRunAtUtc);
            return israelTime.ToString("dd/MM/yyyy HH:mm");
        }
    }

    /// <summary>
    /// Hebrew schedule summary (e.g. "יומי — 09:00", "שבועי — א׳, ג׳ — 14:30",
    /// "חודשי — יום 15 — 08:00", "חד פעמי — 06/04/2026 09:00").
    /// </summary>
    public string FormattedSchedule
    {
        get
        {
            var kindLabel = ScheduleKindLabels.GetValueOrDefault(ScheduleKind, ScheduleKind.ToString());

            return ScheduleKind switch
            {
                Models.ScheduleKind.OneTime => $"{kindLabel} — {FormattedNextRun}",
                Models.ScheduleKind.Daily   => $"{kindLabel} — {FormatTime()}",
                Models.ScheduleKind.Weekly  => $"{kindLabel} — {FormatWeekdays()} — {FormatTime()}",
                Models.ScheduleKind.Monthly => $"{kindLabel} — יום {DayOfMonth} — {FormatTime()}",
                _ => kindLabel,
            };
        }
    }

    private string FormatTime()
    {
        return TimeOfDayMinutes.HasValue
            ? IsraelTimeHelper.FormatTimeOfDay(TimeOfDayMinutes.Value)
            : "";
    }

    private string FormatWeekdays()
    {
        if (!WeekdayMask.HasValue || WeekdayMask.Value == 0)
            return "";

        var mask = WeekdayMask.Value;
        var days = new List<string>();

        // bit 0 = Sunday (א׳) … bit 6 = Saturday (ש׳)
        for (var i = 0; i < 7; i++)
        {
            if ((mask & (1 << i)) != 0)
                days.Add(HebrewDayAbbreviations[i]);
        }

        return string.Join(", ", days);
    }
}
