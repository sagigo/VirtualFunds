using Postgrest.Attributes;
using Postgrest.Models;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Represents a user's portfolio — a named collection of funds (E4.3.1).
/// <para>
/// Portfolios can be "closed" (soft-deleted) by setting <see cref="ClosedAtUtc"/>.
/// A closed portfolio is read-only: no mutations, but history remains queryable.
/// </para>
/// </summary>
[Table("portfolios")]
public class Portfolio : BaseModel
{
    /// <summary>Unique identifier for this portfolio.</summary>
    [PrimaryKey("portfolio_id")]
    public Guid PortfolioId { get; set; }

    /// <summary>User-visible display name of the portfolio.</summary>
    [Column("name")]
    public string Name { get; set; } = string.Empty;

    /// <summary>When the portfolio was created (UTC).</summary>
    [Column("created_at_utc")]
    public DateTime CreatedAtUtc { get; set; }

    /// <summary>When the portfolio was last modified (UTC).</summary>
    [Column("updated_at_utc")]
    public DateTime UpdatedAtUtc { get; set; }

    /// <summary>
    /// When the portfolio was closed (soft-deleted), or <c>null</c> if still active.
    /// </summary>
    [Column("closed_at_utc")]
    public DateTime? ClosedAtUtc { get; set; }

    /// <summary>Whether this portfolio has been closed (soft-deleted).</summary>
    public bool IsClosed => ClosedAtUtc is not null;
}
