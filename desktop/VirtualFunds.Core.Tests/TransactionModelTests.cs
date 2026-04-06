using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for computed display properties on <see cref="TransactionGroup"/>
/// and <see cref="TransactionDetailItem"/>.
/// </summary>
public class TransactionModelTests
{
    // -----------------------------------------------------------------------------------------
    // TransactionDetailItem — FormattedSignedAmount
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void DetailItem_FormattedSignedAmount_Positive_AddsPlusPrefix()
    {
        var item = new TransactionDetailItem { AmountAgoras = 10000 };
        Assert.StartsWith("+", item.FormattedSignedAmount);
        Assert.Contains("100.00 ₪", item.FormattedSignedAmount);
    }

    [Fact]
    public void DetailItem_FormattedSignedAmount_Negative_NoExtraPrefix()
    {
        var item = new TransactionDetailItem { AmountAgoras = -5050 };
        Assert.StartsWith("-", item.FormattedSignedAmount);
        Assert.Contains("50.50 ₪", item.FormattedSignedAmount);
    }

    [Fact]
    public void DetailItem_FormattedSignedAmount_Zero_AddsPlusPrefix()
    {
        var item = new TransactionDetailItem { AmountAgoras = 0 };
        Assert.StartsWith("+", item.FormattedSignedAmount);
        Assert.Contains("0.00 ₪", item.FormattedSignedAmount);
    }

    // -----------------------------------------------------------------------------------------
    // TransactionDetailItem — FormattedBeforeBalance / FormattedAfterBalance
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void DetailItem_FormattedBeforeBalance_NullReturnsEmpty()
    {
        var item = new TransactionDetailItem { BeforeBalanceAgoras = null };
        Assert.Equal(string.Empty, item.FormattedBeforeBalance);
    }

    [Fact]
    public void DetailItem_FormattedBeforeBalance_ValueReturnsFormatted()
    {
        var item = new TransactionDetailItem { BeforeBalanceAgoras = 5000 };
        Assert.Equal("50.00 ₪", item.FormattedBeforeBalance);
    }

    [Fact]
    public void DetailItem_FormattedAfterBalance_NullReturnsEmpty()
    {
        var item = new TransactionDetailItem { AfterBalanceAgoras = null };
        Assert.Equal(string.Empty, item.FormattedAfterBalance);
    }

    [Fact]
    public void DetailItem_FormattedAfterBalance_ValueReturnsFormatted()
    {
        var item = new TransactionDetailItem { AfterBalanceAgoras = 15000 };
        Assert.Equal("150.00 ₪", item.FormattedAfterBalance);
    }

    // -----------------------------------------------------------------------------------------
    // TransactionDetailItem — TransactionTypeLabel
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void DetailItem_TransactionTypeLabel_KnownType_ReturnsHebrew()
    {
        var item = new TransactionDetailItem { TransactionType = "TransferCredit" };
        Assert.Equal("העברה — זיכוי", item.TransactionTypeLabel);
    }

    [Fact]
    public void DetailItem_TransactionTypeLabel_UnknownType_ReturnsRawType()
    {
        var item = new TransactionDetailItem { TransactionType = "UnknownDetailType" };
        Assert.Equal("UnknownDetailType", item.TransactionTypeLabel);
    }

    // -----------------------------------------------------------------------------------------
    // TransactionDetailItem — FormattedAmount
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void DetailItem_FormattedAmount_FormatsCorrectly()
    {
        var item = new TransactionDetailItem { AmountAgoras = 123456 };
        Assert.Equal("1,234.56 ₪", item.FormattedAmount);
    }

    // -----------------------------------------------------------------------------------------
    // TransactionGroup — HasDetails
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Group_HasDetails_FalseWhenEmpty()
    {
        var group = new TransactionGroup { Details = Array.Empty<TransactionDetailItem>() };
        Assert.False(group.HasDetails);
    }

    [Fact]
    public void Group_HasDetails_TrueWhenNotEmpty()
    {
        var group = new TransactionGroup
        {
            Details = new[] { new TransactionDetailItem() }
        };
        Assert.True(group.HasDetails);
    }

    // -----------------------------------------------------------------------------------------
    // TransactionGroup — FormattedAmount
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Group_FormattedAmount_FormatsCorrectly()
    {
        var group = new TransactionGroup { AmountAgoras = 50000 };
        Assert.Equal("500.00 ₪", group.FormattedAmount);
    }

    [Fact]
    public void Group_FormattedAmount_NegativeFormatsCorrectly()
    {
        var group = new TransactionGroup { AmountAgoras = -10050 };
        Assert.Equal("-100.50 ₪", group.FormattedAmount);
    }

    // -----------------------------------------------------------------------------------------
    // TransactionGroup — TransactionTypeLabel
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Group_TransactionTypeLabel_KnownType_ReturnsHebrew()
    {
        var group = new TransactionGroup { TransactionType = "FundDeposit" };
        Assert.Equal("הפקדה", group.TransactionTypeLabel);
    }

    [Fact]
    public void Group_TransactionTypeLabel_UnknownType_ReturnsRawType()
    {
        var group = new TransactionGroup { TransactionType = "FutureType" };
        Assert.Equal("FutureType", group.TransactionTypeLabel);
    }
}
