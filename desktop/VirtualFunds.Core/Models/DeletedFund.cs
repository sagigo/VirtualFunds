using Postgrest.Attributes;
using Postgrest.Models;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Tombstone record preserving fund name information after deletion (E4.3.3).
/// Used for history rendering when a fund no longer exists in the <c>funds</c> table (E7.7).
/// </summary>
[Table("deleted_funds")]
public class DeletedFund : BaseModel
{
    /// <summary>Unique identifier for this tombstone row.</summary>
    [PrimaryKey("deleted_fund_id")]
    public Guid DeletedFundId { get; set; }

    /// <summary>The user who owned the fund.</summary>
    [Column("user_id")]
    public Guid UserId { get; set; }

    /// <summary>The portfolio the fund belonged to.</summary>
    [Column("portfolio_id")]
    public Guid PortfolioId { get; set; }

    /// <summary>The original fund_id (matches fund_id in transaction detail rows).</summary>
    [Column("fund_id")]
    public Guid FundId { get; set; }

    /// <summary>The display name the fund had when it was deleted.</summary>
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    /// <summary>Lowercase, trimmed name (same normalization as funds table).</summary>
    [Column("normalized_name")]
    public string NormalizedName { get; set; } = string.Empty;

    /// <summary>When the fund was deleted (UTC).</summary>
    [Column("deleted_at_utc")]
    public DateTime DeletedAtUtc { get; set; }
}
