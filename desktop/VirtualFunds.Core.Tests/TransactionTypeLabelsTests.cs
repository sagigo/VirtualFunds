using VirtualFunds.Core.Models;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="TransactionTypeLabels"/> — label lookup and filter option list (E5.2, E7.6).
/// </summary>
public class TransactionTypeLabelsTests
{
    // -----------------------------------------------------------------------------------------
    // GetLabel — all known types
    // -----------------------------------------------------------------------------------------

    // xUnit [InlineData] doesn't support enum literals in attributes, so each type gets its own fact.

    [Fact] public void GetLabel_FundDeposit_ReturnsHebrew() => Assert.Equal("הפקדה", TransactionTypeLabels.GetLabel(TransactionType.FundDeposit));
    [Fact] public void GetLabel_FundWithdrawal_ReturnsHebrew() => Assert.Equal("משיכה", TransactionTypeLabels.GetLabel(TransactionType.FundWithdrawal));
    [Fact] public void GetLabel_Transfer_ReturnsHebrew() => Assert.Equal("העברה", TransactionTypeLabels.GetLabel(TransactionType.Transfer));
    [Fact] public void GetLabel_TransferCredit_ReturnsHebrew() => Assert.Equal("העברה — זיכוי", TransactionTypeLabels.GetLabel(TransactionType.TransferCredit));
    [Fact] public void GetLabel_TransferDebit_ReturnsHebrew() => Assert.Equal("העברה — חיוב", TransactionTypeLabels.GetLabel(TransactionType.TransferDebit));
    [Fact] public void GetLabel_PortfolioRevalued_ReturnsHebrew() => Assert.Equal("עדכון שווי", TransactionTypeLabels.GetLabel(TransactionType.PortfolioRevalued));
    [Fact] public void GetLabel_RevaluationCredit_ReturnsHebrew() => Assert.Equal("עדכון שווי — זיכוי", TransactionTypeLabels.GetLabel(TransactionType.RevaluationCredit));
    [Fact] public void GetLabel_RevaluationDebit_ReturnsHebrew() => Assert.Equal("עדכון שווי — חיוב", TransactionTypeLabels.GetLabel(TransactionType.RevaluationDebit));
    [Fact] public void GetLabel_FundCreated_ReturnsHebrew() => Assert.Equal("יצירת קרן", TransactionTypeLabels.GetLabel(TransactionType.FundCreated));
    [Fact] public void GetLabel_FundRenamed_ReturnsHebrew() => Assert.Equal("שינוי שם קרן", TransactionTypeLabels.GetLabel(TransactionType.FundRenamed));
    [Fact] public void GetLabel_FundDeleted_ReturnsHebrew() => Assert.Equal("מחיקת קרן", TransactionTypeLabels.GetLabel(TransactionType.FundDeleted));
    [Fact] public void GetLabel_PortfolioCreated_ReturnsHebrew() => Assert.Equal("יצירת תיק", TransactionTypeLabels.GetLabel(TransactionType.PortfolioCreated));
    [Fact] public void GetLabel_PortfolioRenamed_ReturnsHebrew() => Assert.Equal("שינוי שם תיק", TransactionTypeLabels.GetLabel(TransactionType.PortfolioRenamed));
    [Fact] public void GetLabel_PortfolioClosed_ReturnsHebrew() => Assert.Equal("סגירת תיק", TransactionTypeLabels.GetLabel(TransactionType.PortfolioClosed));
    [Fact] public void GetLabel_ScheduledDepositExecuted_ReturnsHebrew() => Assert.Equal("הפקדה מתוזמנת", TransactionTypeLabels.GetLabel(TransactionType.ScheduledDepositExecuted));
    [Fact] public void GetLabel_Undo_ReturnsHebrew() => Assert.Equal("ביטול", TransactionTypeLabels.GetLabel(TransactionType.Undo));

    // -----------------------------------------------------------------------------------------
    // GetFilterOptions — filter list shape (E7.6)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void GetFilterOptions_ReturnsExpectedCount()
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        Assert.Equal(12, options.Count);
    }

    [Fact]
    public void GetFilterOptions_AllDisplayLabelsAreNonEmpty()
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        Assert.All(options, o => Assert.NotEmpty(o.DisplayLabel));
    }

    /// <summary>
    /// Detail-only types (TransferCredit, TransferDebit, RevaluationCredit, RevaluationDebit)
    /// must not appear in the filter options per E7.6.
    /// </summary>
    [Fact] public void GetFilterOptions_DoesNotContain_TransferCredit() => Assert.DoesNotContain(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.TransferCredit);
    [Fact] public void GetFilterOptions_DoesNotContain_TransferDebit() => Assert.DoesNotContain(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.TransferDebit);
    [Fact] public void GetFilterOptions_DoesNotContain_RevaluationCredit() => Assert.DoesNotContain(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.RevaluationCredit);
    [Fact] public void GetFilterOptions_DoesNotContain_RevaluationDebit() => Assert.DoesNotContain(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.RevaluationDebit);

    /// <summary>Each summary-level type that appears in filter options should be present.</summary>
    [Fact] public void GetFilterOptions_Contains_FundDeposit() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.FundDeposit);
    [Fact] public void GetFilterOptions_Contains_FundWithdrawal() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.FundWithdrawal);
    [Fact] public void GetFilterOptions_Contains_Transfer() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.Transfer);
    [Fact] public void GetFilterOptions_Contains_PortfolioRevalued() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.PortfolioRevalued);
    [Fact] public void GetFilterOptions_Contains_FundCreated() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.FundCreated);
    [Fact] public void GetFilterOptions_Contains_FundRenamed() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.FundRenamed);
    [Fact] public void GetFilterOptions_Contains_FundDeleted() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.FundDeleted);
    [Fact] public void GetFilterOptions_Contains_PortfolioCreated() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.PortfolioCreated);
    [Fact] public void GetFilterOptions_Contains_PortfolioRenamed() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.PortfolioRenamed);
    [Fact] public void GetFilterOptions_Contains_PortfolioClosed() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.PortfolioClosed);
    [Fact] public void GetFilterOptions_Contains_ScheduledDepositExecuted() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.ScheduledDepositExecuted);
    [Fact] public void GetFilterOptions_Contains_Undo() => Assert.Contains(TransactionTypeLabels.GetFilterOptions(), o => o.TypeValue == TransactionType.Undo);

    [Fact]
    public void GetFilterOptions_TypeValuesAreUnique()
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        var distinct = options.Select(o => o.TypeValue).Distinct().Count();
        Assert.Equal(options.Count, distinct);
    }
}
