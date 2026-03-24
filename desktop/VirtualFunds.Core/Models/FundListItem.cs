using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Display-oriented model for the fund list within a portfolio (PR-5, E5.9–E5.10).
/// Combines a fund's identity, balance, and derived allocation percentage.
/// <para>
/// Allocation is computed client-side: <c>balance / total</c> (or 0 when total is 0).
/// It is never stored in the database.
/// </para>
/// </summary>
public class FundListItem
{
    /// <summary>Unique identifier of the fund.</summary>
    public Guid FundId { get; init; }

    /// <summary>User-visible display name.</summary>
    public string Name { get; init; } = string.Empty;

    /// <summary>Current fund balance in agoras.</summary>
    public long BalanceAgoras { get; init; }

    /// <summary>
    /// Derived allocation percentage (0–100). Computed as <c>balance / total * 100</c>,
    /// or 0 when the portfolio total is zero (E5.9).
    /// </summary>
    public double AllocationPercent { get; init; }

    /// <summary>When the fund was created (UTC).</summary>
    public DateTime CreatedAtUtc { get; init; }

    /// <summary>
    /// Human-readable formatted balance (e.g. "1,234.56 ₪").
    /// Derived from <see cref="BalanceAgoras"/> using <see cref="MoneyFormatter"/>.
    /// </summary>
    public string FormattedBalance => MoneyFormatter.FormatAgoras(BalanceAgoras);

    /// <summary>
    /// Human-readable allocation percentage (e.g. "33.3%").
    /// Shows one decimal place. Displays "0.0%" when the portfolio total is zero.
    /// </summary>
    public string FormattedAllocation => $"{AllocationPercent:F1}%";
}
