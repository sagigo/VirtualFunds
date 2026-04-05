using System.Text;
using VirtualFunds.Core.Models;

namespace VirtualFunds.Core.Utilities;

/// <summary>
/// Exports transaction history to CSV format (PR-7).
/// Produces one row per detail line, grouped under their summary operation.
/// Uses UTF-8 with BOM for Excel compatibility with Hebrew text.
/// </summary>
public static class TransactionCsvExporter
{
    /// <summary>
    /// Exports a list of transaction groups to a CSV string.
    /// Each operation produces one summary row followed by its detail rows.
    /// </summary>
    /// <param name="groups">The transaction groups to export (pre-filtered).</param>
    /// <returns>CSV content as a UTF-8 string.</returns>
    public static string Export(IReadOnlyList<TransactionGroup> groups)
    {
        var sb = new StringBuilder();

        // Header row.
        sb.AppendLine("תאריך,סוג,סיכום,קרן,סכום,יתרה לפני,יתרה אחרי");

        foreach (var group in groups)
        {
            var date = group.CommittedAtUtc.ToLocalTime().ToString("yyyy-MM-dd HH:mm");
            var type = CsvEscape(group.TransactionTypeLabel);
            var summary = CsvEscape(group.SummaryText ?? string.Empty);
            var amount = MoneyFormatter.FormatAgoras(group.AmountAgoras);

            // Summary row (no fund, no before/after balance).
            sb.AppendLine($"{date},{type},{summary},,{amount},,");

            // Detail rows.
            foreach (var detail in group.Details)
            {
                var detailType = CsvEscape(detail.TransactionTypeLabel);
                var fundName = CsvEscape(detail.FundName);
                var detailAmount = MoneyFormatter.FormatAgoras(detail.AmountAgoras);
                var before = detail.BeforeBalanceAgoras.HasValue
                    ? MoneyFormatter.FormatAgoras(detail.BeforeBalanceAgoras.Value) : string.Empty;
                var after = detail.AfterBalanceAgoras.HasValue
                    ? MoneyFormatter.FormatAgoras(detail.AfterBalanceAgoras.Value) : string.Empty;

                sb.AppendLine($"{date},{detailType},,{fundName},{detailAmount},{before},{after}");
            }
        }

        return sb.ToString();
    }

    /// <summary>
    /// Writes the CSV content to a file with UTF-8 BOM encoding.
    /// </summary>
    /// <param name="groups">The transaction groups to export.</param>
    /// <param name="filePath">The output file path.</param>
    public static async Task ExportToFileAsync(IReadOnlyList<TransactionGroup> groups, string filePath)
    {
        var csv = Export(groups);
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
