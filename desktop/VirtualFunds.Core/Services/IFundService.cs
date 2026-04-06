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

    // -----------------------------------------------------------------------------------------
    // Fund money operations (E6.7–E6.10)
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Deposits money into a single fund, increasing the portfolio total (E6.8).
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns the fund.</param>
    /// <param name="fundId">The fund to deposit into.</param>
    /// <param name="amountAgoras">The amount to deposit in agoras (must be &gt; 0).</param>
    /// <returns>The operation_id of the committed operation, for undo stack tracking.</returns>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.FundNotFoundException">Fund not found in the portfolio.</exception>
    /// <exception cref="Exceptions.NegativeFundAmountException">Amount is not positive.</exception>
    Task<Guid> DepositAsync(Guid portfolioId, Guid fundId, long amountAgoras);

    /// <summary>
    /// Withdraws money from a single fund, decreasing the portfolio total (E6.9).
    /// The fund must have sufficient balance.
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns the fund.</param>
    /// <param name="fundId">The fund to withdraw from.</param>
    /// <param name="amountAgoras">The amount to withdraw in agoras (must be &gt; 0).</param>
    /// <returns>The operation_id of the committed operation, for undo stack tracking.</returns>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.FundNotFoundException">Fund not found in the portfolio.</exception>
    /// <exception cref="Exceptions.NegativeFundAmountException">Amount is not positive.</exception>
    /// <exception cref="Exceptions.InsufficientFundBalanceException">The fund balance is insufficient.</exception>
    Task<Guid> WithdrawAsync(Guid portfolioId, Guid fundId, long amountAgoras);

    /// <summary>
    /// Transfers money between two funds in the same portfolio without changing the total (E6.10).
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns both funds.</param>
    /// <param name="sourceFundId">The fund to transfer from.</param>
    /// <param name="destinationFundId">The fund to transfer to.</param>
    /// <param name="amountAgoras">The amount to transfer in agoras (must be &gt; 0).</param>
    /// <returns>The operation_id of the committed operation, for undo stack tracking.</returns>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.FundNotFoundException">A referenced fund was not found.</exception>
    /// <exception cref="Exceptions.NegativeFundAmountException">Amount is not positive.</exception>
    /// <exception cref="Exceptions.InsufficientFundBalanceException">The source fund balance is insufficient.</exception>
    /// <exception cref="Exceptions.SameFundTransferException">Source and destination are the same fund.</exception>
    Task<Guid> TransferAsync(Guid portfolioId, Guid sourceFundId, Guid destinationFundId, long amountAgoras);

    // -----------------------------------------------------------------------------------------
    // Portfolio-level money operations (E6.11)
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Revalues the portfolio total, scaling all fund balances proportionally (E6.11).
    /// The operation preserves ownership proportions as closely as possible using
    /// banker's rounding with deterministic remainder distribution.
    /// The service fetches current fund state internally to ensure freshness.
    /// </summary>
    /// <param name="portfolioId">The portfolio to revalue.</param>
    /// <param name="newTotalAgoras">The desired new total in agoras (must be &gt; 0).</param>
    /// <returns>
    /// The operation_id of the committed operation for undo stack tracking,
    /// or <c>null</c> if the operation was a no-op (same total or all deltas zero).
    /// </returns>
    /// <exception cref="Exceptions.NegativeFundAmountException">New total is not positive.</exception>
    /// <exception cref="Exceptions.PortfolioTotalIsZeroException">Current total is zero (cannot compute proportions).</exception>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    Task<Guid?> RevaluePortfolioAsync(Guid portfolioId, long newTotalAgoras);

    // -----------------------------------------------------------------------------------------
    // Undo (E6.12)
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Reverses a previously committed balance-changing operation by creating compensating
    /// transaction rows with negated amounts (E6.12).
    /// <para>
    /// Queries the original detail rows for the given operation, negates each amount,
    /// and commits the compensating operation via <c>rpc_commit_fund_operation</c>
    /// with transaction type "Undo" and <c>undo_of_operation_id</c> set to the original.
    /// </para>
    /// </summary>
    /// <param name="portfolioId">The portfolio that owns the operation.</param>
    /// <param name="originalOperationId">The operation_id to undo (from the local undo stack).</param>
    /// <exception cref="Exceptions.PortfolioClosedException">The portfolio has been closed.</exception>
    /// <exception cref="Exceptions.InsufficientFundBalanceException">
    /// Applying compensating amounts would create a negative fund balance.
    /// </exception>
    Task UndoOperationAsync(Guid portfolioId, Guid originalOperationId);
}
