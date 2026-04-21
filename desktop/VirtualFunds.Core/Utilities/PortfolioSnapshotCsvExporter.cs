using System.Text;
using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Exports the current portfolio fund list as a snapshot CSV (PR-9, E9.2).
/// Produces one row per fund with the portfolio context repeated on each row.
/// Uses UTF-8 with BOM for Excel compatibility with Hebrew text.
/// </summary>
public static class PortfolioSnapshotCsvExporter
{
    /// <summary>
    /// Exports a fund list snapshot to a CSV string.
    /// </summary>
    /// <param name="portfolioId">The portfolio UUID (written to every row).</param>
    /// <param name="portfolioName">The portfolio display name (written to every row).</param>
    /// <param name="funds">The current fund list — order is preserved as-is from the UI.</param>
    /// <returns>CSV content as a UTF-8 string.</returns>
    public static string Export(Guid portfolioId, string portfolioName, IReadOnlyList<FundListItem> funds)
    {
        var sb = new StringBuilder();

        // Header row (Hebrew, matching the transaction history export convention).
        sb.AppendLine("שם תיק,מזהה תיק,שם קרן,מזהה קרן,יתרה,אחוז הקצאה");

        var portfolioIdStr = portfolioId.ToString();
        var portfolioNameEsc = CsvEscape(portfolioName);

        foreach (var fund in funds)
        {
            var fundName = CsvEscape(fund.Name);
            var fundId = fund.FundId.ToString();
            var balance = CsvEscape(fund.FormattedBalance);           // e.g. "1,234.56 ₪"
            var allocation = CsvEscape(fund.FormattedAllocation);     // e.g. "33.3%"

            sb.AppendLine($"{portfolioNameEsc},{portfolioIdStr},{fundName},{fundId},{balance},{allocation}");
        }

        return sb.ToString();
    }

    /// <summary>
    /// Writes the CSV content to a file with UTF-8 BOM encoding.
    /// </summary>
    /// <param name="portfolioId">The portfolio UUID.</param>
    /// <param name="portfolioName">The portfolio display name.</param>
    /// <param name="funds">The current fund list.</param>
    /// <param name="filePath">The output file path.</param>
    public static async Task ExportToFileAsync(
        Guid portfolioId,
        string portfolioName,
        IReadOnlyList<FundListItem> funds,
        string filePath)
    {
        var csv = Export(portfolioId, portfolioName, funds);
        await File.WriteAllTextAsync(filePath, csv, new UTF8Encoding(encoderShouldEmitUTF8Identifier: true))
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Escapes a value for CSV: wraps in quotes if it contains commas, quotes, or newlines.
    /// </summary>
    private static string CsvEscape(string value)
    {
        if (value.Contains(',') || value.Contains('"') || value.Contains('\n') || value.Contains('\r'))
        {
            return $"\"{value.Replace("\"", "\"\"")}\"";
        }
        return value;
    }
}
