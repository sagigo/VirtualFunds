namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Maps canonical <c>transaction_type</c> values (E5.2) to Hebrew display labels for the UI.
/// Also provides the list of filterable summary types for the history filter (E7.6).
/// </summary>
public static class TransactionTypeLabels
{
    /// <summary>
    /// All transaction types and their Hebrew labels.
    /// Covers both summary and detail types from E5.2.
    /// </summary>
    private static readonly Dictionary<string, string> Labels = new()
    {
        // Portfolio structural
        ["PortfolioCreated"] = "יצירת תיק",
        ["PortfolioRenamed"] = "שינוי שם תיק",
        ["PortfolioClosed"] = "סגירת תיק",

        // Fund structural
        ["FundCreated"] = "יצירת קרן",
        ["FundRenamed"] = "שינוי שם קרן",
        ["FundDeleted"] = "מחיקת קרן",

        // Fund money operations
        ["FundDeposit"] = "הפקדה",
        ["FundWithdrawal"] = "משיכה",
        ["Transfer"] = "העברה",
        ["TransferCredit"] = "העברה — זיכוי",
        ["TransferDebit"] = "העברה — חיוב",

        // Revaluation
        ["PortfolioRevalued"] = "עדכון שווי",
        ["RevaluationCredit"] = "עדכון שווי — זיכוי",
        ["RevaluationDebit"] = "עדכון שווי — חיוב",

        // Scheduled
        ["ScheduledDepositExecuted"] = "הפקדה מתוזמנת",

        // Undo
        ["Undo"] = "ביטול",
    };

    /// <summary>
    /// Gets the Hebrew label for a transaction type.
    /// Returns the raw type string if no label is defined.
    /// </summary>
    public static string GetLabel(string transactionType) =>
        Labels.TryGetValue(transactionType, out var label) ? label : transactionType;

    /// <summary>
    /// Returns the summary-level transaction types that can be used as filter options (E7.6).
    /// Detail-only types (TransferCredit, TransferDebit, RevaluationCredit, RevaluationDebit)
    /// do not appear as standalone filter options.
    /// </summary>
    public static IReadOnlyList<TransactionTypeFilter> GetFilterOptions()
    {
        return
        [
            new("FundDeposit", Labels["FundDeposit"]),
            new("FundWithdrawal", Labels["FundWithdrawal"]),
            new("Transfer", Labels["Transfer"]),
            new("PortfolioRevalued", Labels["PortfolioRevalued"]),
            new("FundCreated", Labels["FundCreated"]),
            new("FundRenamed", Labels["FundRenamed"]),
            new("FundDeleted", Labels["FundDeleted"]),
            new("PortfolioCreated", Labels["PortfolioCreated"]),
            new("PortfolioRenamed", Labels["PortfolioRenamed"]),
            new("PortfolioClosed", Labels["PortfolioClosed"]),
            new("ScheduledDepositExecuted", Labels["ScheduledDepositExecuted"]),
            new("Undo", Labels["Undo"]),
        ];
    }
}

/// <summary>
/// A filterable transaction type for the history type dropdown.
/// </summary>
/// <param name="TypeValue">The canonical transaction_type string (e.g. "FundDeposit").</param>
/// <param name="DisplayLabel">The Hebrew label shown in the UI (e.g. "הפקדה").</param>
public record TransactionTypeFilter(string TypeValue, string DisplayLabel);
