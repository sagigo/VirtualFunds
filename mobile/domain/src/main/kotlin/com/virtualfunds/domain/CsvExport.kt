package com.virtualfunds.domain

import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.model.TransactionGroup
import com.virtualfunds.domain.model.TransactionTypeLabels
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure CSV builders for the two exports (PR-9, E9). No Android, no I/O — they return the CSV
 * text; the `:app` layer is responsible for writing the bytes (with a UTF-8 BOM) to the
 * user-chosen file.
 *
 * Mirrors the desktop `TransactionCsvExporter` / `PortfolioSnapshotCsvExporter`. Money columns
 * are CSV-escaped on both platforms: amounts ≥ ₪1,000 format with a thousands comma
 * ("1,234.56 ₪") that would otherwise split the field. (The desktop transaction exporter
 * originally omitted this escaping; fixed during the Phase 11 parity review.)
 */

/** Row separator: CRLF per RFC 4180 (and matching the desktop's StringBuilder.AppendLine). */
private const val CSV_NEWLINE = "\r\n"

/**
 * Date-time pattern for the history "תאריך" column (mirrors desktop `ToString("yyyy-MM-dd HH:mm")`).
 * Numeric-only, so the formatter emits ASCII digits regardless of locale (DecimalStyle.STANDARD).
 */
private val CSV_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * Builds the transaction-history CSV (E9.1): a header row, then for each operation one summary
 * row followed by its per-fund detail rows. `groups` is already filtered to the view being
 * exported (E9.1 "currently displayed (filtered) view").
 *
 * @param groups pre-filtered, newest-first transaction groups.
 * @param zoneId timezone for the date column — defaults to the device zone (matching the desktop's
 *   `ToLocalTime()`), but injectable so unit tests are deterministic.
 */
fun buildTransactionHistoryCsv(
    groups: List<TransactionGroup>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val sb = StringBuilder()
    sb.append("תאריך,סוג,סיכום,קרן,סכום,יתרה לפני,יתרה אחרי").append(CSV_NEWLINE)

    for (group in groups) {
        val date = group.committedAtUtc.atZone(zoneId).format(CSV_DATE_TIME_FORMATTER)
        val type = csvEscape(TransactionTypeLabels.label(group.transactionType))
        val summary = csvEscape(group.summaryText ?: "")
        val amount = csvEscape(formatAgoras(group.amountAgoras))

        // Summary row: no fund / before / after columns.
        sb.append(date).append(',').append(type).append(',').append(summary)
            .append(",,").append(amount).append(",,").append(CSV_NEWLINE)

        // Detail rows: no summary column; fund + before/after when available.
        for (detail in group.details) {
            val detailType = csvEscape(TransactionTypeLabels.label(detail.transactionType))
            val fundName = csvEscape(detail.fundName)
            val detailAmount = csvEscape(formatAgoras(detail.amountAgoras))
            val before = detail.beforeBalanceAgoras?.let { csvEscape(formatAgoras(it)) } ?: ""
            val after = detail.afterBalanceAgoras?.let { csvEscape(formatAgoras(it)) } ?: ""

            sb.append(date).append(",").append(detailType).append(",,")
                .append(fundName).append(',').append(detailAmount).append(',')
                .append(before).append(',').append(after).append(CSV_NEWLINE)
        }
    }
    return sb.toString()
}

/**
 * Builds the portfolio-snapshot CSV (E9.2): a header row, then one row per fund with the
 * portfolio context repeated. `funds` is exported in the order given (the current UI sort order).
 * Allocation percent is derived at export time from the supplied [FundListItem.allocationPercent].
 */
fun buildPortfolioSnapshotCsv(
    portfolioId: String,
    portfolioName: String,
    funds: List<FundListItem>,
): String {
    val sb = StringBuilder()
    sb.append("שם תיק,מזהה תיק,שם קרן,מזהה קרן,יתרה,אחוז הקצאה").append(CSV_NEWLINE)

    val nameEsc = csvEscape(portfolioName)
    val idEsc = csvEscape(portfolioId)
    for (fund in funds) {
        val fundName = csvEscape(fund.name)
        val fundId = csvEscape(fund.fundId)
        val balance = csvEscape(formatAgoras(fund.balanceAgoras))
        val allocation = csvEscape(formatAllocationPercent(fund.allocationPercent))

        sb.append(nameEsc).append(',').append(idEsc).append(',')
            .append(fundName).append(',').append(fundId).append(',')
            .append(balance).append(',').append(allocation).append(CSV_NEWLINE)
    }
    return sb.toString()
}

/**
 * Allocation percent for export: one decimal place, e.g. "33.3%". Mirrors the desktop's
 * `FormattedAllocation` (`$"{AllocationPercent:F1}%"`). Locale.ROOT keeps the decimal separator a
 * '.' (never a ',', which would otherwise need CSV-quoting) and the digits ASCII.
 */
private fun formatAllocationPercent(percent: Double): String =
    String.format(Locale.ROOT, "%.1f%%", percent)

/**
 * Escapes a CSV field per RFC 4180: if it contains a comma, double-quote, CR or LF, wrap it in
 * double-quotes and double any embedded quotes. Mirrors the desktop `CsvEscape`.
 */
private fun csvEscape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
