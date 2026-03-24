using Postgrest;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Supabase-backed implementation of <see cref="IFundService"/> (PR-5, E5.6–E5.8).
/// Reads funds via Postgrest and mutates via RPC functions.
/// </summary>
public sealed class SupabaseFundService : IFundService
{
    private readonly global::Supabase.Client _client;

    /// <summary>
    /// Initializes the service with the Supabase client (injected from DI).
    /// </summary>
    public SupabaseFundService(global::Supabase.Client client)
    {
        _client = client;
    }

    /// <inheritdoc />
    public async Task<IReadOnlyList<FundListItem>> GetFundsAsync(Guid portfolioId)
    {
        var response = await _client.From<Fund>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Order("normalized_name", Constants.Ordering.Ascending)
            .Get()
            .ConfigureAwait(false);

        var funds = response.Models;

        if (funds.Count == 0)
            return Array.Empty<FundListItem>();

        // Compute portfolio total for allocation percentages (E5.9).
        var total = funds.Sum(f => f.BalanceAgoras);

        return funds
            .Select(f => new FundListItem
            {
                FundId = f.FundId,
                Name = f.Name,
                BalanceAgoras = f.BalanceAgoras,
                AllocationPercent = total > 0
                    ? (double)f.BalanceAgoras / total * 100.0
                    : 0.0,
                CreatedAtUtc = f.CreatedAtUtc,
            })
            .ToList();
    }

    /// <inheritdoc />
    public async Task<Guid> CreateFundAsync(Guid portfolioId, string name, long initialAmountAgoras)
    {
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        try
        {
            var response = await _client.Rpc(
                "rpc_create_fund",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_name = name,
                    p_initial_amount_agoras = initialAmountAgoras,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                }).ConfigureAwait(false);

            // The RPC returns a UUID (the new fund_id) as a JSON string.
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
    public async Task RenameFundAsync(Guid portfolioId, Guid fundId, string newName)
    {
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        try
        {
            await _client.Rpc(
                "rpc_rename_fund",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_fund_id = fundId,
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
    public async Task DeleteFundAsync(Guid portfolioId, Guid fundId)
    {
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        try
        {
            await _client.Rpc(
                "rpc_delete_fund",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_fund_id = fundId,
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
            throw new EmptyFundNameException();

        if (message.Contains("ERR_VALIDATION:DUPLICATE_NAME"))
            throw new DuplicateFundNameException();

        if (message.Contains("ERR_VALIDATION:NEGATIVE_AMOUNT"))
            throw new NegativeFundAmountException();

        if (message.Contains("ERR_VALIDATION:FUND_NOT_EMPTY"))
            throw new FundNotEmptyException();

        if (message.Contains("ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT"))
            throw new FundHasScheduledDepositException();

        if (message.Contains("ERR_VALIDATION:PORTFOLIO_CLOSED"))
            throw new PortfolioClosedException();

        if (message.Contains("ERR_NOT_FOUND"))
            throw new FundNotFoundException();

        // Unknown RPC error — re-throw the original exception.
        throw ex;
    }
}
