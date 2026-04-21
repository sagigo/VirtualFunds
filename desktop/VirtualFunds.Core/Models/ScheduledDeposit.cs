using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Postgrest.Attributes;
using Postgrest.Models;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Represents a scheduled automatic deposit into a fund (E4.3.5, E8).
/// <para>
/// Maps directly to the <c>scheduled_deposits</c> table. Each row defines a recurring
/// (or one-time) deposit that the device triggers via <c>rpc_execute_due_scheduled_deposits</c>.
/// </para>
/// </summary>
[Table("scheduled_deposits")]
public class ScheduledDeposit : BaseModel
{
    /// <summary>Unique identifier for this scheduled deposit.</summary>
    [PrimaryKey("scheduled_deposit_id")]
    public Guid ScheduledDepositId { get; set; }

    /// <summary>The portfolio this deposit belongs to.</summary>
    [Column("portfolio_id")]
    public Guid PortfolioId { get; set; }

    /// <summary>The target fund to deposit into.</summary>
    [Column("fund_id")]
    public Guid FundId { get; set; }

    /// <summary>User-visible display name of the scheduled deposit.</summary>
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    /// <summary>Optional note attached to each execution.</summary>
    [Column("note")]
    public string? Note { get; set; }

    /// <summary>Whether the scheduled deposit is active and will execute on schedule.</summary>
    [Column("is_enabled")]
    public bool IsEnabled { get; set; }

    /// <summary>Amount to deposit per execution, in agoras (always positive).</summary>
    [Column("amount_agoras")]
    public long AmountAgoras { get; set; }

    /// <summary>The schedule kind (E8.2): OneTime, Daily, Weekly, or Monthly.</summary>
    [JsonConverter(typeof(StringEnumConverter))]
    [Column("schedule_kind")]
    public ScheduleKind ScheduleKind { get; set; }

    /// <summary>
    /// Minutes since midnight in Israel time for the scheduled execution time (0–1439).
    /// Required for Daily, Weekly, Monthly; null for OneTime.
    /// </summary>
    [Column("time_of_day_minutes")]
    public int? TimeOfDayMinutes { get; set; }

    /// <summary>
    /// Bitmask of weekdays for Weekly schedules (bit 0 = Sunday … bit 6 = Saturday).
    /// Valid range: 1–127 when not null.
    /// </summary>
    [Column("weekday_mask")]
    public int? WeekdayMask { get; set; }

    /// <summary>
    /// Day of the month for Monthly schedules (1–28).
    /// Capped at 28 to avoid month-length issues.
    /// </summary>
    [Column("day_of_month")]
    public int? DayOfMonth { get; set; }

    /// <summary>Next scheduled execution time in UTC.</summary>
    [Column("next_run_at_utc")]
    public DateTime NextRunAtUtc { get; set; }

    /// <summary>When this scheduled deposit was created (UTC).</summary>
    [Column("created_at_utc")]
    public DateTime CreatedAtUtc { get; set; }

    /// <summary>When this scheduled deposit was last modified (UTC).</summary>
    [Column("updated_at_utc")]
    public DateTime UpdatedAtUtc { get; set; }
}
