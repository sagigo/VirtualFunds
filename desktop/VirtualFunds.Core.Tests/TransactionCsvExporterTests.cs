using VirtualFunds.Core.Models;
using VirtualFunds.Core.Utilities;

namespace VirtualFunds.Core.Tests;

/// <summary>
/// Tests for <see cref="TransactionCsvExporter.Export"/> — CSV structure, escaping, and formatting.
/// </summary>
public class TransactionCsvExporterTests
{
    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private static TransactionGroup MakeGroup(
        TransactionType type = TransactionType.FundDeposit,
        string? summaryText = "הפקדה לקרן א",
        long amountAgoras = 10000,
        DateTime? committedAt = null,
        TransactionDetailItem[]? details = null) => new()
    {
        OperationId = Guid.NewGuid(),
        CommittedAtUtc = committedAt ?? new DateTime(2024, 3, 15, 10, 30, 0, DateTimeKind.Utc),
        TransactionType = type,
        SummaryText = summaryText,
        AmountAgoras = amountAgoras,
        Details = details ?? Array.Empty<TransactionDetailItem>(),
    };

    private static TransactionDetailItem MakeDetail(
        string fundName = "קרן א",
        TransactionType type = TransactionType.FundDeposit,
        long amountAgoras = 10000,
        long? beforeBalance = 0,
        long? afterBalance = 10000) => new()
    {
        FundId = Guid.NewGuid(),
        FundName = fundName,
        TransactionType = type,
        AmountAgoras = amountAgoras,
        BeforeBalanceAgoras = beforeBalance,
        AfterBalanceAgoras = afterBalance,
    };

    /// <summary>Splits CSV output into lines, skipping the trailing empty line.</summary>
    private static string[] SplitLines(string csv) =>
        csv.Split('\n', StringSplitOptions.RemoveEmptyEntries)
           .Select(l => l.TrimEnd('\r'))
           .ToArray();

    // -----------------------------------------------------------------------------------------
    // Header
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Export_AlwaysStartsWithHeader()
    {
        var csv = TransactionCsvExporter.Export(Array.Empty<TransactionGroup>());
        var firstLine = SplitLines(csv)[0];

        Assert.Equal("תאריך,סוג,סיכום,קרן,סכום,יתרה לפני,יתרה אחרי", firstLine);
    }

    [Fact]
    public void Export_EmptyList_ReturnsOnlyHeader()
    {
        var csv = TransactionCsvExporter.Export(Array.Empty<TransactionGroup>());
        var lines = SplitLines(csv);

        Assert.Single(lines); // only the header row
    }

    // -----------------------------------------------------------------------------------------
    // Summary row (no details)
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Export_GroupWithNoDetails_ProducesOneSummaryRow()
    {
        var group = MakeGroup(details: Array.Empty<TransactionDetailItem>());
        var csv = TransactionCsvExporter.Export([group]);
        var lines = SplitLines(csv);

        // 1 header + 1 summary row = 2 lines
        Assert.Equal(2, lines.Length);
    }

    [Fact]
    public void Export_SummaryRow_ContainsFormattedAmount()
    {
        var group = MakeGroup(amountAgoras: 12345); // 123.45 ₪
        var csv = TransactionCsvExporter.Export([group]);
        var lines = SplitLines(csv);

        Assert.Contains("123.45 ₪", lines[1]);
    }

    [Fact]
    public void Export_SummaryRow_ContainsSummaryText()
    {
        var group = MakeGroup(summaryText: "הפקדה לקרן");
        var csv = TransactionCsvExporter.Export([group]);
        var lines = SplitLines(csv);

        Assert.Contains("הפקדה לקרן", lines[1]);
    }

    [Fact]
    public void Export_SummaryRow_FundAndBalanceColumnsAreEmpty()
    {
        // Summary row format: date,type,summary,,amount,,
        // Fund name (col 4) and before/after balance (cols 6,7) should be empty.
        var group = MakeGroup(amountAgoras: 10000);
        var csv = TransactionCsvExporter.Export([group]);
        var summaryRow = SplitLines(csv)[1];

        var cols = summaryRow.Split(',');
        Assert.True(cols.Length >= 7, $"Expected at least 7 columns, got: {cols.Length}");
        Assert.Equal(string.Empty, cols[3]); // fund name
        Assert.Equal(string.Empty, cols[5]); // before balance
        Assert.Equal(string.Empty, cols[6]); // after balance
    }

    [Fact]
    public void Export_SummaryRow_NullSummaryText_UsesEmpty()
    {
        var group = MakeGroup(summaryText: null);
        // Should not throw
        var csv = TransactionCsvExporter.Export([group]);
        Assert.NotNull(csv);
    }

    // -----------------------------------------------------------------------------------------
    // Detail rows
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Export_GroupWithOneDetail_ProducesSummaryPlusDetailRow()
    {
        var detail = MakeDetail();
        var group = MakeGroup(details: [detail]);
        var csv = TransactionCsvExporter.Export([group]);
        var lines = SplitLines(csv);

        // 1 header + 1 summary + 1 detail = 3 lines
        Assert.Equal(3, lines.Length);
    }

    [Fact]
    public void Export_DetailRow_ContainsFundName()
    {
        var detail = MakeDetail(fundName: "קרן ב");
        var group = MakeGroup(details: [detail]);
        var csv = TransactionCsvExporter.Export([group]);
        var lines = SplitLines(csv);

        Assert.Contains("קרן ב", lines[2]); // detail row is line index 2
    }

    [Fact]
    public void Export_DetailRow_ContainsBeforeAndAfterBalance()
    {
        var detail = MakeDetail(beforeBalance: 5000, afterBalance: 15000); // 50.00 / 150.00
        var group = MakeGroup(details: [detail]);
        var csv = TransactionCsvExporter.Export([group]);
        var lines = SplitLines(csv);

        Assert.Contains("50.00 ₪", lines[2]);
        Assert.Contains("150.00 ₪", lines[2]);
    }

    [Fact]
    public void Export_DetailRow_NullBeforeBalance_EmptyField()
    {
        var detail = MakeDetail(beforeBalance: null, afterBalance: null);
        var group = MakeGroup(details: [detail]);
        var csv = TransactionCsvExporter.Export([group]);
        var detailRow = SplitLines(csv)[2];

        // before and after should be empty (not "0.00 ₪")
        var cols = detailRow.Split(',');
        Assert.Equal(string.Empty, cols[5]); // before
        Assert.Equal(string.Empty, cols[6]); // after
    }

    [Fact]
    public void Export_MultipleGroups_AllGroupsIncluded()
    {
        var groups = new[]
        {
            MakeGroup(summaryText: "הפקדה"),
            MakeGroup(summaryText: "משיכה"),
        };
        var csv = TransactionCsvExporter.Export(groups);
        var lines = SplitLines(csv);

        // 1 header + 2 summary rows = 3 lines
        Assert.Equal(3, lines.Length);
    }

    // -----------------------------------------------------------------------------------------
    // CSV escaping
    // -----------------------------------------------------------------------------------------

    [Fact]
    public void Export_SummaryTextWithComma_IsQuoted()
    {
        var group = MakeGroup(summaryText: "הפקדה, קרן א");
        var csv = TransactionCsvExporter.Export([group]);

        Assert.Contains("\"הפקדה, קרן א\"", csv);
    }

    [Fact]
    public void Export_SummaryTextWithQuote_IsEscaped()
    {
        var group = MakeGroup(summaryText: "say \"hello\"");
        var csv = TransactionCsvExporter.Export([group]);

        Assert.Contains("\"say \"\"hello\"\"\"", csv);
    }

    [Fact]
    public void Export_SummaryTextWithNewline_IsQuoted()
    {
        var group = MakeGroup(summaryText: "line1\nline2");
        var csv = TransactionCsvExporter.Export([group]);

        Assert.Contains("\"line1\nline2\"", csv);
    }

    [Fact]
    public void Export_FundNameWithComma_IsQuoted()
    {
        var detail = MakeDetail(fundName: "קרן, ב");
        var group = MakeGroup(details: [detail]);
        var csv = TransactionCsvExporter.Export([group]);

        Assert.Contains("\"קרן, ב\"", csv);
    }

    [Fact]
    public void Export_PlainText_IsNotQuoted()
    {
        var group = MakeGroup(summaryText: "הפקדה לקרן");
        var csv = TransactionCsvExporter.Export([group]);

        // Simple text should appear without surrounding quotes
        Assert.Contains(",הפקדה לקרן,", csv);
    }
}
