using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Services;

/// <summary>
/// Service for reading transaction history for a portfolio (PR-7, E7).
/// History is immutable — this service only reads, never writes.
/// Transactions are written by mutation RPCs (fund operations, structural RPCs).
/// </summary>
public interface ITransactionService
{
    /// <summary>
    /// Loads the full transaction history for a portfolio, grouped by operation (E7.6).
    /// Returns operations sorted newest-first by committed_at_utc, then operation_id, then transaction_id.
    /// Fund names are resolved per E7.7 (current name → tombstone → generic label).
    /// </summary>
    /// <param name="portfolioId">The portfolio whose history to load.</param>
    /// <returns>All transaction groups for the portfolio, sorted newest first.</returns>
    Task<IReadOnlyList<TransactionGroup>> GetHistoryAsync(Guid portfolioId);

    /// <summary>
    /// Returns the list of fund names available for filtering, including both active
    /// and deleted funds that appear in this portfolio's history.
    /// </summary>
    /// <param name="portfolioId">The portfolio whose fund names to load.</param>
    /// <returns>A list of (fundId, displayName) pairs for the filter dropdown.</returns>
    Task<IReadOnlyList<FundFilterOption>> GetFundFilterOptionsAsync(Guid portfolioId);
}

/// <summary>
/// A fund available for filtering in the history view.
/// Includes both active and deleted funds.
/// </summary>
/// <param name="FundId">The fund's unique identifier.</param>
/// <param name="DisplayName">The fund's display name (current or tombstone).</param>
public record FundFilterOption(Guid FundId, string DisplayName);
