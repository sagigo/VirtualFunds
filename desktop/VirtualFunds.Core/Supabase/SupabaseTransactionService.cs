using Postgrest;
using VirtualFunds.Core.Models;
using VirtualFunds.Core.Services;

namespace VirtualFunds.Core.Supabase;

/// <summary>
/// Supabase-backed implementation of <see cref="ITransactionService"/> (PR-7, E7).
/// Reads transactions and deleted_funds via Postgrest, groups by operation_id,
/// and resolves fund names per E7.7.
/// </summary>
public sealed class SupabaseTransactionService : ITransactionService
{
    private readonly global::Supabase.Client _client;

    /// <summary>Generic label for funds that can't be resolved (E7.7 step 3).</summary>
    private const string DeletedFundLabel = "(קרן שנמחקה)";

    /// <summary>
    /// Initializes the service with the Supabase client (injected from DI).
    /// </summary>
    public SupabaseTransactionService(global::Supabase.Client client)
    {
        _client = client;
    }

    /// <inheritdoc />
    public async Task<IReadOnlyList<TransactionGroup>> GetHistoryAsync(Guid portfolioId)
    {
        // Step 1: Fetch all transactions for this portfolio, sorted per E7.6.
        var transactionResponse = await _client.From<Transaction>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Order("committed_at_utc", Constants.Ordering.Descending)
            .Order("operation_id", Constants.Ordering.Ascending)
            .Order("transaction_id", Constants.Ordering.Ascending)
            .Get()
            .ConfigureAwait(false);

        var allRows = transactionResponse.Models;

        if (allRows.Count == 0)
            return Array.Empty<TransactionGroup>();

        // Step 2: Build a fund name lookup (E7.7).
        var fundNameMap = await BuildFundNameMapAsync(portfolioId, allRows).ConfigureAwait(false);

        // Step 3: Group by operation_id and build display models.
        var groups = allRows
            .GroupBy(t => t.OperationId)
            .Select(g => BuildGroup(g, fundNameMap))
            .OrderByDescending(g => g.CommittedAtUtc)
            .ThenBy(g => g.OperationId)
            .ToList();

        return groups;
    }

    /// <inheritdoc />
    public async Task<IReadOnlyList<FundFilterOption>> GetFundFilterOptionsAsync(Guid portfolioId)
    {
        // Fetch active funds.
        var fundsResponse = await _client.From<Fund>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Order("normalized_name", Constants.Ordering.Ascending)
            .Get()
            .ConfigureAwait(false);

        var activeFunds = fundsResponse.Models
            .Select(f => new FundFilterOption(f.FundId, f.Name))
            .ToList();

        // Fetch deleted funds (tombstones).
        var deletedResponse = await _client.From<DeletedFund>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Order("name", Constants.Ordering.Ascending)
            .Get()
            .ConfigureAwait(false);

        // Only include deleted funds that aren't also active (the tombstone preserves the last name).
        var activeFundIds = new HashSet<Guid>(activeFunds.Select(f => f.FundId));
        var deletedFunds = deletedResponse.Models
            .Where(d => !activeFundIds.Contains(d.FundId))
            .Select(d => new FundFilterOption(d.FundId, $"{d.Name} (נמחקה)"))
            .ToList();

        // Combine: active first, then deleted.
        activeFunds.AddRange(deletedFunds);
        return activeFunds;
    }

    /// <summary>
    /// Builds a lookup from fund_id to display name, resolving per E7.7:
    /// 1. Current fund name (from funds table)
    /// 2. Tombstone name (from deleted_funds table)
    /// 3. Generic deleted-fund label
    /// </summary>
    private async Task<Dictionary<Guid, string>> BuildFundNameMapAsync(
        Guid portfolioId, List<Transaction> transactions)
    {
        var nameMap = new Dictionary<Guid, string>();

        // Collect all unique fund_ids from detail rows.
        var fundIds = transactions
            .Where(t => t.FundId.HasValue)
            .Select(t => t.FundId!.Value)
            .Distinct()
            .ToList();

        if (fundIds.Count == 0)
            return nameMap;

        // Step 1: Fetch active funds for this portfolio.
        var fundsResponse = await _client.From<Fund>()
            .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
            .Get()
            .ConfigureAwait(false);

        foreach (var fund in fundsResponse.Models)
        {
            nameMap[fund.FundId] = fund.Name;
        }

        // Step 2: For any fund_ids not found in active funds, check deleted_funds.
        var missingIds = fundIds.Where(id => !nameMap.ContainsKey(id)).ToList();

        if (missingIds.Count > 0)
        {
            var deletedResponse = await _client.From<DeletedFund>()
                .Filter("portfolio_id", Constants.Operator.Equals, portfolioId.ToString())
                .Get()
                .ConfigureAwait(false);

            foreach (var deleted in deletedResponse.Models)
            {
                if (!nameMap.ContainsKey(deleted.FundId))
                {
                    nameMap[deleted.FundId] = deleted.Name;
                }
            }
        }

        // Step 3: Any remaining fund_ids get the generic label.
        foreach (var id in fundIds)
        {
            nameMap.TryAdd(id, DeletedFundLabel);
        }

        return nameMap;
    }

    /// <summary>
    /// Builds a <see cref="TransactionGroup"/> from a set of rows sharing the same operation_id.
    /// Expects exactly one Summary row and zero or more Detail rows (E7.2).
    /// </summary>
    private static TransactionGroup BuildGroup(
        IGrouping<Guid, Transaction> group,
        Dictionary<Guid, string> fundNameMap)
    {
        var rows = group.ToList();

        // Find the summary row (there should be exactly one per E7.2).
        var summary = rows.FirstOrDefault(r => r.RecordKind == "Summary");

        // Detail rows: everything that isn't the summary.
        var details = rows
            .Where(r => r.RecordKind == "Detail")
            .Select(r => new TransactionDetailItem
            {
                FundId = r.FundId!.Value,
                FundName = r.FundId.HasValue && fundNameMap.TryGetValue(r.FundId.Value, out var name)
                    ? name
                    : DeletedFundLabel,
                TransactionType = r.TransactionType,
                AmountAgoras = r.AmountAgoras,
                BeforeBalanceAgoras = r.BeforeBalanceAgoras,
                AfterBalanceAgoras = r.AfterBalanceAgoras,
            })
            .ToList();

        return new TransactionGroup
        {
            OperationId = group.Key,
            CommittedAtUtc = summary?.CommittedAtUtc ?? rows.First().CommittedAtUtc,
            TransactionType = summary?.TransactionType ?? rows.First().TransactionType,
            SummaryText = summary?.SummaryText,
            AmountAgoras = summary?.AmountAgoras ?? 0,
            Details = details,
        };
    }

}
