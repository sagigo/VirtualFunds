using Postgrest.Attributes;
using Postgrest.Models;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Represents a fund within a portfolio — a named ownership bucket with a balance (E4.3.2).
/// <para>
/// Funds hold a non-negative balance in agoras. The allocation percentage is derived
/// client-side (E5.9) and not stored in the database.
/// </para>
/// </summary>
[Table("funds")]
public class Fund : BaseModel
{
    /// <summary>Unique identifier for this fund.</summary>
    [PrimaryKey("fund_id")]
    public Guid FundId { get; set; }

    /// <summary>The portfolio this fund belongs to.</summary>
    [Column("portfolio_id")]
    public Guid PortfolioId { get; set; }

    /// <summary>User-visible display name of the fund.</summary>
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    /// <summary>Lowercase, trimmed name used for case-insensitive uniqueness (E2.4).</summary>
    [Column("normalized_name")]
    public string NormalizedName { get; set; } = string.Empty;

    /// <summary>Current fund balance in agoras (always non-negative).</summary>
    [Column("balance_agoras")]
    public long BalanceAgoras { get; set; }

    /// <summary>When the fund was created (UTC).</summary>
    [Column("created_at_utc")]
    public DateTime CreatedAtUtc { get; set; }

    /// <summary>When the fund was last modified (UTC).</summary>
    [Column("updated_at_utc")]
    public DateTime UpdatedAtUtc { get; set; }
}
