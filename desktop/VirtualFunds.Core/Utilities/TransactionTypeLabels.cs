using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Maps <see cref="TransactionType"/> enum values (E5.2) to Hebrew display labels for the UI.
/// Also provides the list of filterable summary types for the history filter (E7.6).
/// </summary>
public static class TransactionTypeLabels
{
    /// <summary>
    /// All transaction types and their Hebrew labels.
    /// Covers both summary and detail types from E5.2.
    /// </summary>
    private static readonly Dictionary<TransactionType, string> Labels = new()
    {
        // Portfolio structural
        [TransactionType.PortfolioCreated] = "יצירת תיק",
        [TransactionType.PortfolioRenamed] = "שינוי שם תיק",
        [TransactionType.PortfolioClosed] = "סגירת תיק",

        // Fund structural
        [TransactionType.FundCreated] = "יצירת קרן",
        [TransactionType.FundRenamed] = "שינוי שם קרן",
        [TransactionType.FundDeleted] = "מחיקת קרן",

        // Fund money operations
        [TransactionType.FundDeposit] = "הפקדה",
        [TransactionType.FundWithdrawal] = "משיכה",
        [TransactionType.Transfer] = "העברה",
        [TransactionType.TransferCredit] = "העברה — זיכוי",
        [TransactionType.TransferDebit] = "העברה — חיוב",

        // Revaluation
        [TransactionType.PortfolioRevalued] = "עדכון שווי",
        [TransactionType.RevaluationCredit] = "עדכון שווי — זיכוי",
        [TransactionType.RevaluationDebit] = "עדכון שווי — חיוב",

        // Scheduled
        [TransactionType.ScheduledDepositExecuted] = "הפקדה מתוזמנת",

        // Undo
        [TransactionType.Undo] = "ביטול",
    };

    /// <summary>
    /// Gets the Hebrew label for a transaction type.
    /// Returns the enum name as a fallback if no label is defined.
    /// </summary>
    public static string GetLabel(TransactionType transactionType) =>
        Labels.TryGetValue(transactionType, out var label) ? label : transactionType.ToString();

    /// <summary>
    /// Returns the summary-level transaction types that can be used as filter options (E7.6).
    /// Detail-only types (TransferCredit, TransferDebit, RevaluationCredit, RevaluationDebit)
    /// do not appear as standalone filter options.
    /// </summary>
    public static IReadOnlyList<TransactionTypeFilter> GetFilterOptions()
    {
        return
        [
            new(TransactionType.FundDeposit,              Labels[TransactionType.FundDeposit]),
            new(TransactionType.FundWithdrawal,           Labels[TransactionType.FundWithdrawal]),
            new(TransactionType.Transfer,                 Labels[TransactionType.Transfer]),
            new(TransactionType.PortfolioRevalued,        Labels[TransactionType.PortfolioRevalued]),
            new(TransactionType.FundCreated,              Labels[TransactionType.FundCreated]),
            new(TransactionType.FundRenamed,              Labels[TransactionType.FundRenamed]),
            new(TransactionType.FundDeleted,              Labels[TransactionType.FundDeleted]),
            new(TransactionType.PortfolioCreated,         Labels[TransactionType.PortfolioCreated]),
            new(TransactionType.PortfolioRenamed,         Labels[TransactionType.PortfolioRenamed]),
            new(TransactionType.PortfolioClosed,          Labels[TransactionType.PortfolioClosed]),
            new(TransactionType.ScheduledDepositExecuted, Labels[TransactionType.ScheduledDepositExecuted]),
            new(TransactionType.Undo,                     Labels[TransactionType.Undo]),
        ];
    }
}

/// <summary>
/// A filterable transaction type for the history type dropdown.
/// </summary>
/// <param name="TypeValue">The transaction type enum value (e.g. <see cref="TransactionType.FundDeposit"/>).</param>
/// <param name="DisplayLabel">The Hebrew label shown in the UI (e.g. "הפקדה").</param>
public record TransactionTypeFilter(TransactionType TypeValue, string DisplayLabel);
