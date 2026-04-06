using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Display-oriented model grouping all transaction rows for one logical operation (E7.2).
/// Contains exactly one summary row and zero or more detail rows, all sharing the same
/// <see cref="OperationId"/>.
/// </summary>
public class TransactionGroup
{
    /// <summary>The shared operation_id that groups these rows.</summary>
    public Guid OperationId { get; init; }

    /// <summary>When the operation was committed (server timestamp, E7.5).</summary>
    public DateTime CommittedAtUtc { get; init; }

    /// <summary>
    /// The transaction type from the summary row (e.g. "FundDeposit", "Transfer").
    /// Used for type filtering (E7.6).
    /// </summary>
    public string TransactionType { get; init; } = string.Empty;

    /// <summary>Hebrew display label for the transaction type.</summary>
    public string TransactionTypeLabel => TransactionTypeLabels.GetLabel(TransactionType);

    /// <summary>The server-generated summary text for this operation.</summary>
    public string? SummaryText { get; init; }

    /// <summary>
    /// If this operation is an undo, references the original operation_id it reversed.
    /// Null for non-undo operations. Used to determine which operations have already been undone.
    /// </summary>
    public Guid? UndoOfOperationId { get; init; }

    /// <summary>
    /// True if this operation can be undone: it is an undoable type and has not already
    /// been undone. Computed by TransactionHistoryViewModel after loading all groups.
    /// </summary>
    public bool IsUndoable { get; set; }

    /// <summary>
    /// The net amount from the summary row in agoras.
    /// For operations like deposit/withdrawal this is the total amount moved.
    /// </summary>
    public long AmountAgoras { get; init; }

    /// <summary>Formatted net amount (e.g. "100.00 ₪").</summary>
    public string FormattedAmount => MoneyFormatter.FormatAgoras(AmountAgoras);

    /// <summary>The per-fund detail rows for this operation.</summary>
    public IReadOnlyList<TransactionDetailItem> Details { get; init; } = Array.Empty<TransactionDetailItem>();

    /// <summary>True if this operation has detail rows to show.</summary>
    public bool HasDetails => Details.Count > 0;
}

/// <summary>
/// Display-oriented model for a single detail row within a transaction group (E7.2).
/// Contains the per-fund effect with resolved fund name (E7.7).
/// </summary>
public class TransactionDetailItem
{
    /// <summary>The fund affected by this detail row.</summary>
    public Guid FundId { get; init; }

    /// <summary>
    /// Resolved fund name per E7.7:
    /// 1. Current fund name (if fund still exists)
    /// 2. Tombstone name from deleted_funds
    /// 3. Generic label "(קרן שנמחקה)" if neither found
    /// </summary>
    public string FundName { get; init; } = string.Empty;

    /// <summary>The detail-level transaction type (e.g. "TransferCredit", "RevaluationDebit").</summary>
    public string TransactionType { get; init; } = string.Empty;

    /// <summary>Hebrew display label for the detail transaction type.</summary>
    public string TransactionTypeLabel => TransactionTypeLabels.GetLabel(TransactionType);

    /// <summary>The amount change in agoras (positive = credit, negative = debit).</summary>
    public long AmountAgoras { get; init; }

    /// <summary>Formatted amount change without explicit sign (e.g. "100.00 ₪" or "-50.00 ₪").</summary>
    public string FormattedAmount => MoneyFormatter.FormatAgoras(AmountAgoras);

    /// <summary>Formatted signed amount change for the history detail row (e.g. "+100.00 ₪" or "-50.00 ₪").</summary>
    public string FormattedSignedAmount =>
        AmountAgoras >= 0
            ? "+" + MoneyFormatter.FormatAgoras(AmountAgoras)
            : MoneyFormatter.FormatAgoras(AmountAgoras);

    /// <summary>Fund balance before this change (may be null for structural events).</summary>
    public long? BeforeBalanceAgoras { get; init; }

    /// <summary>Fund balance after this change (may be null for structural events).</summary>
    public long? AfterBalanceAgoras { get; init; }

    /// <summary>Formatted before-balance, or empty if null.</summary>
    public string FormattedBeforeBalance =>
        BeforeBalanceAgoras.HasValue ? MoneyFormatter.FormatAgoras(BeforeBalanceAgoras.Value) : string.Empty;

    /// <summary>Formatted after-balance, or empty if null.</summary>
    public string FormattedAfterBalance =>
        AfterBalanceAgoras.HasValue ? MoneyFormatter.FormatAgoras(AfterBalanceAgoras.Value) : string.Empty;
}
