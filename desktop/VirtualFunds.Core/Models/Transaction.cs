using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Postgrest.Attributes;
using Postgrest.Models;

namespace VirtualFunds.Core.Models;

/// <summary>
/// Represents a single row in the <c>transactions</c> table — the immutable audit log (E4.3.4).
/// <para>
/// Every balance-changing or structural action produces one summary row and zero or more
/// detail rows, all sharing the same <see cref="OperationId"/>.
/// </para>
/// </summary>
[Table("transactions")]
public class Transaction : BaseModel
{
    /// <summary>Unique identifier for this transaction row.</summary>
    [PrimaryKey("transaction_id")]
    public Guid TransactionId { get; set; }

    /// <summary>The user who performed the action.</summary>
    [Column("user_id")]
    public Guid UserId { get; set; }

    /// <summary>The portfolio this transaction belongs to.</summary>
    [Column("portfolio_id")]
    public Guid PortfolioId { get; set; }

    /// <summary>Groups all rows for one logical action (summary + details).</summary>
    [Column("operation_id")]
    public Guid OperationId { get; set; }

    /// <summary>Server-generated timestamp for when the operation was committed (E7.5).</summary>
    [Column("committed_at_utc")]
    public DateTime CommittedAtUtc { get; set; }

    /// <summary>
    /// Either <see cref="Models.RecordKind.Summary"/> (one per operation) or
    /// <see cref="Models.RecordKind.Detail"/> (per-fund effect).
    /// See E7.2 for the summary/detail model.
    /// </summary>
    [JsonConverter(typeof(StringEnumConverter))]
    [Column("record_kind")]
    public RecordKind RecordKind { get; set; }

    /// <summary>
    /// The type of transaction. See E5.2 for the canonical registry
    /// and <see cref="Models.TransactionType"/> for all valid values.
    /// </summary>
    [JsonConverter(typeof(StringEnumConverter))]
    [Column("transaction_type")]
    public TransactionType TransactionType { get; set; }

    /// <summary>
    /// The fund affected by this row. Null for summary rows, non-null for detail rows (E7.2).
    /// Not a foreign key — funds may be deleted while history remains (E4.3.4 design note).
    /// </summary>
    [Column("fund_id")]
    public Guid? FundId { get; set; }

    /// <summary>
    /// The amount change in agoras. Positive for credits, negative for debits.
    /// Structural detail rows may use 0 (E7.3).
    /// </summary>
    [Column("amount_agoras")]
    public long AmountAgoras { get; set; }

    /// <summary>Fund balance before this transaction (detail rows only).</summary>
    [Column("before_balance_agoras")]
    public long? BeforeBalanceAgoras { get; set; }

    /// <summary>Fund balance after this transaction (detail rows only).</summary>
    [Column("after_balance_agoras")]
    public long? AfterBalanceAgoras { get; set; }

    /// <summary>
    /// If this operation is an undo/compensation, references the original operation_id.
    /// </summary>
    [Column("undo_of_operation_id")]
    public Guid? UndoOfOperationId { get; set; }

    /// <summary>
    /// Human-readable summary of the action (summary rows only).
    /// </summary>
    [Column("summary_text")]
    public string? SummaryText { get; set; }

    /// <summary>Optional user-provided note for the operation.</summary>
    [Column("note")]
    public string? Note { get; set; }
}
