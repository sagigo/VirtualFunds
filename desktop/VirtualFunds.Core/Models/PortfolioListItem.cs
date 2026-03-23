using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Display-oriented model for the portfolio list screen.
/// Combines a portfolio's identity/name with its computed total balance.
/// <para>
/// The total is derived from <c>SUM(funds.balance_agoras)</c> — it is not stored on the
/// <c>portfolios</c> table itself (per E5.9, portfolio total = sum of fund balances).
/// </para>
/// </summary>
public class PortfolioListItem
{
    /// <summary>Unique identifier of the portfolio.</summary>
    public Guid PortfolioId { get; init; }

    /// <summary>User-visible display name.</summary>
    public string Name { get; init; } = string.Empty;

    /// <summary>Sum of all fund balances in this portfolio, in agoras.</summary>
    public long TotalBalanceAgoras { get; init; }

    /// <summary>
    /// Human-readable formatted total (e.g. "1,234.56 ₪").
    /// Derived from <see cref="TotalBalanceAgoras"/> using <see cref="MoneyFormatter"/>.
    /// </summary>
    public string FormattedTotal => MoneyFormatter.FormatAgoras(TotalBalanceAgoras);
}
