using Postgrest;
using Postgrest.Attributes;
using Postgrest.Models;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Supabase-backed implementation of <see cref="IPortfolioService"/> (PR-4, E5.3–E5.5).
/// Reads portfolios via Postgrest and mutates via RPC functions.
/// </summary>
public sealed class SupabasePortfolioService : IPortfolioService
{
    private readonly global::Supabase.Client _client;

    /// <summary>
    /// Initializes the service with the Supabase client (injected from DI).
    /// </summary>
    public SupabasePortfolioService(global::Supabase.Client client)
    {
        _client = client;
    }

    /// <inheritdoc />
    public async Task<IReadOnlyList<PortfolioListItem>> GetActivePortfoliosAsync()
    {
        // Fetch active portfolios.
        var portfolioResponse = await _client.From<Portfolio>()
            .Filter<string>("closed_at_utc", Constants.Operator.Is, null)
            .Order("normalized_name", Constants.Ordering.Ascending)
            .Get()
            .ConfigureAwait(false);

        var portfolios = portfolioResponse.Models;

        if (portfolios.Count == 0)
            return Array.Empty<PortfolioListItem>();

        // Fetch all funds to compute per-portfolio totals.
        // RLS ensures we only see the authenticated user's funds.
        var fundsResponse = await _client.From<FundBalanceRow>()
            .Select("fund_id,portfolio_id,balance_agoras")
            .Get()
            .ConfigureAwait(false);

        var totalsByPortfolio = fundsResponse.Models
            .GroupBy(f => f.PortfolioId)
            .ToDictionary(g => g.Key, g => g.Sum(f => f.BalanceAgoras));

        return portfolios
            .Select(p => new PortfolioListItem
            {
                PortfolioId = p.PortfolioId,
                Name = p.Name,
                TotalBalanceAgoras = totalsByPortfolio.GetValueOrDefault(p.PortfolioId, 0),
            })
            .ToList();
    }

    /// <inheritdoc />
    public async Task<Guid> CreatePortfolioAsync(string name)
    {
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        try
        {
            var response = await _client.Rpc(
                "rpc_create_portfolio",
                new
                {
                    p_name = name,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                }).ConfigureAwait(false);

            // The RPC returns a UUID (the new portfolio_id) as a JSON string.
            var content = response.Content?.Trim('"') ?? string.Empty;
            return Guid.Parse(content);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw; // Unreachable, but satisfies the compiler.
        }
    }

    /// <inheritdoc />
    public async Task RenamePortfolioAsync(Guid portfolioId, string newName)
    {
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        try
        {
            await _client.Rpc(
                "rpc_rename_portfolio",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_new_name = newName,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }
    }

    /// <inheritdoc />
    public async Task ClosePortfolioAsync(Guid portfolioId)
    {
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        try
        {
            await _client.Rpc(
                "rpc_close_portfolio",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Error mapping
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Checks whether an exception originates from a Supabase Postgrest RPC call.
    /// The SDK wraps RPC errors in various exception types whose messages contain the
    /// error tokens raised by our PL/pgSQL functions.
    /// </summary>
    private static bool IsRpcException(Exception ex)
    {
        // Postgrest exceptions and general HTTP errors from the SDK both carry the
        // error token in their Message or InnerException.
        return ex.Message.Contains("ERR_") || ex.InnerException?.Message.Contains("ERR_") == true;
    }

    /// <summary>
    /// Parses the RPC error token from the exception message and throws the corresponding
    /// typed exception. Error tokens are defined in E4.7.
    /// </summary>
    internal static void ThrowForRpcError(Exception ex)
    {
        var message = ex.Message + (ex.InnerException?.Message ?? string.Empty);

        if (message.Contains("ERR_VALIDATION:EMPTY_NAME"))
            throw new EmptyPortfolioNameException();

        if (message.Contains("ERR_VALIDATION:DUPLICATE_NAME"))
            throw new DuplicatePortfolioNameException();

        if (message.Contains("ERR_VALIDATION:PORTFOLIO_CLOSED"))
            throw new PortfolioClosedException();

        if (message.Contains("ERR_NOT_FOUND"))
            throw new PortfolioNotFoundException();

        // Unknown RPC error — re-throw the original exception.
        throw ex;
    }

    // -----------------------------------------------------------------------------------------
    // Internal Postgrest model for querying fund balances
    // -----------------------------------------------------------------------------------------

    /// <summary>
    /// Minimal model for reading fund balances from the <c>funds</c> table.
    /// Used only to compute per-portfolio totals — not a full Fund model.
    /// </summary>
    [Table("funds")]
    internal class FundBalanceRow : BaseModel
    {
        /// <summary>Fund identifier (primary key).</summary>
        [PrimaryKey("fund_id")]
        public Guid FundId { get; set; }

        /// <summary>The portfolio this fund belongs to.</summary>
        [Column("portfolio_id")]
        public Guid PortfolioId { get; set; }

        /// <summary>Current fund balance in agoras.</summary>
        [Column("balance_agoras")]
        public long BalanceAgoras { get; set; }
    }
}
