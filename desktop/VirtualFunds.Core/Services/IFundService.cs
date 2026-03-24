using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Services;

/// <summary>
/// Service for fund CRUD operations within a portfolio (PR-5, E5.6–E5.8).
/// All mutations are executed via Supabase RPC and produce transaction history on the server.
/// </summary>
public interface IFundService
{
    /// <summary>
    /// Loads all funds for the given portfolio, sorted by normalized name ascending (E5.10).
    /// Each fund includes its derived allocation percentage (E5.9).
    /// </summary>
    /// <param name="portfolioId">The portfolio whose funds to load.</param>
    /// <returns>A read-only list of fund display items. Empty list if no funds exist.</returns>
    Task<IReadOnlyList<FundListItem>> GetFundsAsync(Guid portfolioId);

    /// <summary>
    /// Creates a new fund in the portfolio, optionally with an initial balance (E5.6).
    /// The name is trimmed and validated for uniqueness (case-insensitive) on the server.
    /// </summary>
    /// <param name="portfolioId">The portfolio to create the fund in.</param>
    /// <param name="name">The display name for the new fund.</param>
    /// <param name="initialAmountAgoras">Initial balance in agoras (must be >= 0). Pass 0 for no initial balance.</param>
    /// <returns>The ID of the newly created fund.</returns>
    /// <exception cref="Exceptions.EmptyFundNameException">Name is empty or whitespace.</exception>
    /// <exception cref="Exceptions.DuplicateFundNameException">A fund with this name already exists in the portfolio.</exception>
    /// <exception cref="Exceptions.NegativeFundAmountException">The initial amount is negative.</exception>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    Task<Guid> CreateFundAsync(Guid portfolioId, string name, long initialAmountAgoras);

    /// <summary>
    /// Renames an existing fund within the portfolio (E5.7).
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns the fund.</param>
    /// <param name="fundId">The fund to rename.</param>
    /// <param name="newName">The new display name.</param>
    /// <exception cref="Exceptions.EmptyFundNameException">New name is empty or whitespace.</exception>
    /// <exception cref="Exceptions.DuplicateFundNameException">Another fund with this name exists in the portfolio.</exception>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.FundNotFoundException">Fund not found in the portfolio.</exception>
    Task RenameFundAsync(Guid portfolioId, Guid fundId, string newName);

    /// <summary>
    /// Deletes a fund from the portfolio (E5.8).
    /// The fund must have zero balance and no enabled scheduled deposits.
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns the fund.</param>
    /// <param name="fundId">The fund to delete.</param>
    /// <exception cref="Exceptions.FundNotEmptyException">The fund's balance is not zero.</exception>
    /// <exception cref="Exceptions.FundHasScheduledDepositException">The fund has an enabled scheduled deposit.</exception>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.FundNotFoundException">Fund not found in the portfolio.</exception>
    Task DeleteFundAsync(Guid portfolioId, Guid fundId);
}
