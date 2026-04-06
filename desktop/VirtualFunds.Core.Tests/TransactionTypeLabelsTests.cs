using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="TransactionTypeLabels"/> — label lookup and filter option list (E5.2, E7.6).
/// </summary>
public class TransactionTypeLabelsTests
{
    // -----------------------------------------------------------------------------------------
    // GetLabel — known types
    // -----------------------------------------------------------------------------------------

    [Theory]
    [InlineData("FundDeposit", "הפקדה")]
    [InlineData("FundWithdrawal", "משיכה")]
    [InlineData("Transfer", "העברה")]
    [InlineData("TransferCredit", "העברה — זיכוי")]
    [InlineData("TransferDebit", "העברה — חיוב")]
    [InlineData("PortfolioRevalued", "עדכון שווי")]
    [InlineData("RevaluationCredit", "עדכון שווי — זיכוי")]
    [InlineData("RevaluationDebit", "עדכון שווי — חיוב")]
    [InlineData("FundCreated", "יצירת קרן")]
    [InlineData("FundRenamed", "שינוי שם קרן")]
    [InlineData("FundDeleted", "מחיקת קרן")]
    [InlineData("PortfolioCreated", "יצירת תיק")]
    [InlineData("PortfolioRenamed", "שינוי שם תיק")]
    [InlineData("PortfolioClosed", "סגירת תיק")]
    [InlineData("ScheduledDepositExecuted", "הפקדה מתוזמנת")]
    [InlineData("Undo", "ביטול")]
    public void GetLabel_KnownType_ReturnsHebrew(string type, string expectedHebrew)
    {
        Assert.Equal(expectedHebrew, TransactionTypeLabels.GetLabel(type));
    }

    [Fact]
    public void GetLabel_UnknownType_ReturnsRawType()
    {
        const string unknown = "SomeFutureType";
        Assert.Equal(unknown, TransactionTypeLabels.GetLabel(unknown));
    }

    [Fact]
    public void GetLabel_EmptyString_ReturnsEmptyString()
    {
        Assert.Equal(string.Empty, TransactionTypeLabels.GetLabel(string.Empty));
    }

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

    [Fact]
    public void GetFilterOptions_AllTypeValuesAreNonEmpty()
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        Assert.All(options, o => Assert.NotEmpty(o.TypeValue));
    }

    /// <summary>
    /// Detail-only types (TransferCredit, TransferDebit, RevaluationCredit, RevaluationDebit)
    /// must not appear in the filter options per E7.6.
    /// </summary>
    [Theory]
    [InlineData("TransferCredit")]
    [InlineData("TransferDebit")]
    [InlineData("RevaluationCredit")]
    [InlineData("RevaluationDebit")]
    public void GetFilterOptions_DoesNotContainDetailOnlyTypes(string detailOnlyType)
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        Assert.DoesNotContain(options, o => o.TypeValue == detailOnlyType);
    }

    /// <summary>Each summary-level type that appears in filter options should have a non-null label.</summary>
    [Theory]
    [InlineData("FundDeposit")]
    [InlineData("FundWithdrawal")]
    [InlineData("Transfer")]
    [InlineData("PortfolioRevalued")]
    [InlineData("FundCreated")]
    [InlineData("FundRenamed")]
    [InlineData("FundDeleted")]
    [InlineData("PortfolioCreated")]
    [InlineData("PortfolioRenamed")]
    [InlineData("PortfolioClosed")]
    [InlineData("ScheduledDepositExecuted")]
    [InlineData("Undo")]
    public void GetFilterOptions_ContainsSummaryType(string summaryType)
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        Assert.Contains(options, o => o.TypeValue == summaryType);
    }

    [Fact]
    public void GetFilterOptions_TypeValuesAreUnique()
    {
        var options = TransactionTypeLabels.GetFilterOptions();
        var distinct = options.Select(o => o.TypeValue).Distinct().Count();
        Assert.Equal(options.Count, distinct);
    }
}
