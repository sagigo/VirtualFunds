using Postgrest;
using VirtualFunds.Core.Exceptions;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Supabase-backed implementation of <see cref="IFundService"/> (PR-5, E5.6–E5.8, E6.7–E6.10).
/// Reads funds via Postgrest. Structural mutations use dedicated RPCs; money operations
/// (deposit, withdraw, transfer) use the generic <c>rpc_commit_fund_operation</c> engine.
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
    // Fund money operations (E6.7–E6.10)
    // -----------------------------------------------------------------------------------------

    /// <inheritdoc />
    public async Task<Guid> DepositAsync(Guid portfolioId, Guid fundId, long amountAgoras)
    {
        if (amountAgoras <= 0)
            throw new NegativeFundAmountException();

        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();
        var detailTransactionId = OperationIdGenerator.NewTransactionId();

        var details = new[]
        {
            new
            {
                transaction_id = detailTransactionId,
                transaction_type = "FundDeposit",
                fund_id = fundId,
                amount_agoras = amountAgoras,
            },
        };

        try
        {
            await _client.Rpc(
                "rpc_commit_fund_operation",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                    p_summary_transaction_type = "FundDeposit",
                    p_summary_text = "הפקדה לקרן",
                    p_details = details,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }

        return operationId;
    }

    /// <inheritdoc />
    public async Task<Guid> WithdrawAsync(Guid portfolioId, Guid fundId, long amountAgoras)
    {
        if (amountAgoras <= 0)
            throw new NegativeFundAmountException();

        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();
        var detailTransactionId = OperationIdGenerator.NewTransactionId();

        var details = new[]
        {
            new
            {
                transaction_id = detailTransactionId,
                transaction_type = "FundWithdrawal",
                fund_id = fundId,
                amount_agoras = -amountAgoras, // Negative delta — reduces balance.
            },
        };

        try
        {
            await _client.Rpc(
                "rpc_commit_fund_operation",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                    p_summary_transaction_type = "FundWithdrawal",
                    p_summary_text = "משיכה מקרן",
                    p_details = details,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }

        return operationId;
    }

    /// <inheritdoc />
    public async Task<Guid> TransferAsync(Guid portfolioId, Guid sourceFundId, Guid destinationFundId, long amountAgoras)
    {
        if (sourceFundId == destinationFundId)
            throw new SameFundTransferException();

        if (amountAgoras <= 0)
            throw new NegativeFundAmountException();

        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();
        var debitTransactionId = OperationIdGenerator.NewTransactionId();
        var creditTransactionId = OperationIdGenerator.NewTransactionId();

        var details = new[]
        {
            new
            {
                transaction_id = debitTransactionId,
                transaction_type = "TransferDebit",
                fund_id = sourceFundId,
                amount_agoras = -amountAgoras, // Source loses money.
            },
            new
            {
                transaction_id = creditTransactionId,
                transaction_type = "TransferCredit",
                fund_id = destinationFundId,
                amount_agoras = amountAgoras, // Destination gains money.
            },
        };

        try
        {
            await _client.Rpc(
                "rpc_commit_fund_operation",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                    p_summary_transaction_type = "Transfer",
                    p_summary_text = "העברה בין קרנות",
                    p_details = details,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }

        return operationId;
    }

    // -----------------------------------------------------------------------------------------
    // Portfolio-level money operations (E6.11)
    // -----------------------------------------------------------------------------------------

    /// <inheritdoc />
    public async Task<Guid?> RevaluePortfolioAsync(Guid portfolioId, long newTotalAgoras)
    {
        if (newTotalAgoras <= 0)
            throw new NegativeFundAmountException();

        // Step 1: Fetch current fund state (ensures freshness).
        var funds = await GetFundsAsync(portfolioId).ConfigureAwait(false);

        if (funds.Count == 0)
            throw new PortfolioTotalIsZeroException();

        // Step 2: Compute old total.
        var oldTotalAgoras = funds.Sum(f => f.BalanceAgoras);

        if (oldTotalAgoras == 0)
            throw new PortfolioTotalIsZeroException();

        // Step 3: No-op when totals are equal — don't log anything.
        if (newTotalAgoras == oldTotalAgoras)
            return null;

        // Step 4: Compute provisional new balances using banker's rounding.
        // Uses decimal arithmetic to avoid floating-point precision issues.
        var workItems = funds
            .Select(f => new
            {
                f.FundId,
                OldBalance = f.BalanceAgoras,
                ProvisionalNewBalance = (long)Math.Round(
                    (decimal)newTotalAgoras * f.BalanceAgoras / oldTotalAgoras,
                    0,
                    MidpointRounding.ToEven),
            })
            .ToList();

        // Step 5–6: Compute remainder.
        var provisionalSum = workItems.Sum(w => w.ProvisionalNewBalance);
        var remainder = newTotalAgoras - provisionalSum;

        // Step 7: Sort by fund_id ascending for deterministic remainder distribution.
        var sorted = workItems.OrderBy(w => w.FundId).ToList();

        // Build mutable list of final balances.
        var finalBalances = sorted
            .Select(w => (w.FundId, w.OldBalance, NewBalance: w.ProvisionalNewBalance))
            .ToList();

        // Step 8: Fix remainder deterministically.
        if (remainder > 0)
        {
            for (var i = 0; i < (int)remainder; i++)
            {
                var item = finalBalances[i];
                finalBalances[i] = (item.FundId, item.OldBalance, item.NewBalance + 1);
            }
        }
        else if (remainder < 0)
        {
            var absRemainder = (int)(-remainder);
            var adjusted = 0;
            for (var i = 0; i < finalBalances.Count && adjusted < absRemainder; i++)
            {
                if (finalBalances[i].NewBalance > 0)
                {
                    var item = finalBalances[i];
                    finalBalances[i] = (item.FundId, item.OldBalance, item.NewBalance - 1);
                    adjusted++;
                }
            }
        }

        // Validate: block if any fund with a non-zero balance would be reduced to zero.
        // A zero-balance fund can never recover via future revaluations (0 * ratio = 0).
        if (finalBalances.Any(fb => fb.OldBalance > 0 && fb.NewBalance == 0))
            throw new RevalueWouldZeroFundException();

        // Steps 9–10: Compute deltas and build detail rows for non-zero deltas.
        var operationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        var details = finalBalances
            .Select(fb => new { Delta = fb.NewBalance - fb.OldBalance, fb.FundId })
            .Where(d => d.Delta != 0)
            .Select(d => new
            {
                transaction_id = OperationIdGenerator.NewTransactionId(),
                transaction_type = d.Delta > 0 ? "RevaluationCredit" : "RevaluationDebit",
                fund_id = d.FundId,
                amount_agoras = d.Delta,
            })
            .ToArray();

        // All deltas zero after rounding — treat as no-op.
        if (details.Length == 0)
            return null;

        // Steps 11–12: Call RPC.
        var summaryText = $"עדכון שווי תיק מ-{MoneyFormatter.FormatAgoras(oldTotalAgoras)} ל-{MoneyFormatter.FormatAgoras(newTotalAgoras)}";

        try
        {
            await _client.Rpc(
                "rpc_commit_fund_operation",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_operation_id = operationId,
                    p_summary_transaction_id = summaryTransactionId,
                    p_summary_transaction_type = "PortfolioRevalued",
                    p_summary_text = summaryText,
                    p_details = details,
                }).ConfigureAwait(false);
        }
        catch (Exception ex) when (IsRpcException(ex))
        {
            ThrowForRpcError(ex);
            throw;
        }

        return operationId;
    }

    // -----------------------------------------------------------------------------------------
    // Undo (E6.12)
    // -----------------------------------------------------------------------------------------

    /// <inheritdoc />
    public async Task UndoOperationAsync(Guid portfolioId, Guid originalOperationId)
    {
        // Step 2 (E6.12): Query the original detail rows for the operation.
        var response = await _client.From<Transaction>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Filter("operation_id", Constants.Operator.Equals, originalOperationId.ToString())
            .Filter("record_kind", Constants.Operator.Equals, "Detail")
            .Get()
            .ConfigureAwait(false);

        var originalDetails = response.Models;

        // Step 3: Create new operation/transaction IDs.
        var undoOperationId = OperationIdGenerator.NewOperationId();
        var summaryTransactionId = OperationIdGenerator.NewTransactionId();

        // Steps 4-5: Build compensating detail rows with negated amounts.
        var details = originalDetails
            .Select(d => new
            {
                transaction_id = OperationIdGenerator.NewTransactionId(),
                transaction_type = "Undo",
                fund_id = d.FundId!.Value, // ! safe: filtered to Detail rows which always have fund_id
                amount_agoras = -d.AmountAgoras, // Negate the original amount.
                undo_of_operation_id = originalOperationId,
            })
            .ToArray();

        // Step 6: Commit via RPC. The server validates no negative balances (step 5).
        try
        {
            await _client.Rpc(
                "rpc_commit_fund_operation",
                new
                {
                    p_portfolio_id = portfolioId,
                    p_operation_id = undoOperationId,
                    p_summary_transaction_id = summaryTransactionId,
                    p_summary_transaction_type = "Undo",
                    p_summary_text = "ביטול פעולה",
                    p_undo_of_operation_id = originalOperationId,
                    p_details = details,
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
        var msg = ex.Message;
        var inner = ex.InnerException?.Message;
        return msg.Contains("ERR_") || inner?.Contains("ERR_") == true;
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

        if (message.Contains("ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO"))
            throw new PortfolioTotalIsZeroException();

        if (message.Contains("ERR_NOT_FOUND"))
            throw new FundNotFoundException();

        if (message.Contains("ERR_INVARIANT:NEGATIVE_BALANCE"))
            throw new InsufficientFundBalanceException();

        if (message.Contains("ERR_INVARIANT:TOTAL_MISMATCH"))
            throw new TotalMismatchException();

        // Unknown RPC error — re-throw the original exception.
        throw ex;
    }
}
