using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Services;

/// <summary>
/// Service for portfolio CRUD operations (PR-4, E5.3–E5.5).
/// All mutations are executed via Supabase RPC and produce transaction history on the server.
/// </summary>
public interface IPortfolioService
{
    /// <summary>
    /// Loads all active (non-closed) portfolios for the authenticated user,
    /// sorted alphabetically by normalized name, each with its computed total balance.
    /// </summary>
    /// <returns>A read-only list of portfolio list items. Empty list if the user has none.</returns>
    Task<IReadOnlyList<PortfolioListItem>> GetActivePortfoliosAsync();

    /// <summary>
    /// Creates a new portfolio with the given name (E5.3).
    /// The name is trimmed and validated for uniqueness (case-insensitive) on the server.
    /// </summary>
    /// <param name="name">The display name for the new portfolio.</param>
    /// <returns>The ID of the newly created portfolio.</returns>
    /// <exception cref="Exceptions.EmptyPortfolioNameException">Name is empty or whitespace.</exception>
    /// <exception cref="Exceptions.DuplicatePortfolioNameException">An active portfolio with this name already exists.</exception>
    Task<Guid> CreatePortfolioAsync(string name);

    /// <summary>
    /// Renames an existing active portfolio (E5.4).
    /// </summary>
    /// <param name="portfolioId">The portfolio to rename.</param>
    /// <param name="newName">The new display name.</param>
    /// <exception cref="Exceptions.EmptyPortfolioNameException">New name is empty or whitespace.</exception>
    /// <exception cref="Exceptions.DuplicatePortfolioNameException">Another active portfolio with this name exists.</exception>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.PortfolioNotFoundException">Portfolio not found or not owned by user.</exception>
    Task RenamePortfolioAsync(Guid portfolioId, string newName);

    /// <summary>
    /// Closes (soft-deletes) a portfolio (E5.5).
    /// This also disables all scheduled deposits in the portfolio.
    /// </summary>
    /// <param name="portfolioId">The portfolio to close.</param>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio is already closed.</exception>
    /// <exception cref="Exceptions.PortfolioNotFoundException">Portfolio not found or not owned by user.</exception>
    Task ClosePortfolioAsync(Guid portfolioId);
}
