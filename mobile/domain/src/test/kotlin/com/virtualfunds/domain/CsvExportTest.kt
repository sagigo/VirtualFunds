package com.virtualfunds.domain

import com.virtualfunds.domain.model.FundListItem
import com.virtualfunds.domain.model.TransactionDetailItem
import com.virtualfunds.domain.model.TransactionGroup
import com.virtualfunds.domain.model.TransactionType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for the CSV builders (E9.1 / E9.2).
 *
 * A fixed UTC [ZoneId] is injected so the date column is deterministic regardless of where the
 * test runs. Key behaviours covered: header rows, summary-then-detail layout, RFC-4180 escaping
 * (including the money columns, which contain a thousands-comma at ≥ ₪1,000), and the allocation
 * format.
 */
class CsvExportTest : FunSpec({

    val utc = ZoneId.of("UTC")
    val instant = Instant.parse("2026-06-28T10:00:00Z")

    // ── Transaction history (E9.1) ────────────────────────────────────────────────

    test("history CSV starts with the Hebrew header row and CRLF") {
        buildTransactionHistoryCsv(emptyList(), utc) shouldBe
            "תאריך,סוג,סיכום,קרן,סכום,יתרה לפני,יתרה אחרי\r\n"
    }

    test("history CSV emits a summary row followed by its detail rows") {
        val groups = listOf(
            TransactionGroup(
                operationId = "op1",
                committedAtUtc = instant,
                transactionType = TransactionType.FundDeposit,
                summaryText = "הפקדה לקרן",
                undoOfOperationId = null,
                amountAgoras = 5000L, // 50.00 ₪ — no comma, so unquoted
                details = listOf(
                    TransactionDetailItem(
                        fundId = "f1",
                        fundName = "מניות",
                        transactionType = TransactionType.FundDeposit,
                        amountAgoras = 5000L,
                        beforeBalanceAgoras = 0L,
                        afterBalanceAgoras = 5000L,
                    ),
                ),
                isUndoable = true,
            ),
        )
        val lines = buildTransactionHistoryCsv(groups, utc).split("\r\n")

        lines[0] shouldBe "תאריך,סוג,סיכום,קרן,סכום,יתרה לפני,יתרה אחרי"
        // Summary: date, type, summary, (empty fund), amount, (empty before/after)
        lines[1] shouldBe "2026-06-28 10:00,הפקדה,הפקדה לקרן,,50.00 ₪,,"
        // Detail: date, type, (empty summary), fund, amount, before, after
        lines[2] shouldBe "2026-06-28 10:00,הפקדה,,מניות,50.00 ₪,0.00 ₪,50.00 ₪"
    }

    test("money columns ≥ ₪1,000 are quoted so the thousands-comma does not break the row") {
        val groups = listOf(
            TransactionGroup(
                operationId = "op1",
                committedAtUtc = instant,
                transactionType = TransactionType.FundDeposit,
                summaryText = null,
                undoOfOperationId = null,
                amountAgoras = 123456L, // 1,234.56 ₪
                details = emptyList(),
                isUndoable = true,
            ),
        )
        val summaryLine = buildTransactionHistoryCsv(groups, utc).split("\r\n")[1]
        summaryLine shouldBe "2026-06-28 10:00,הפקדה,,,\"1,234.56 ₪\",,"
    }

    test("summary text containing a comma is quoted") {
        val groups = listOf(
            TransactionGroup(
                operationId = "op1",
                committedAtUtc = instant,
                transactionType = TransactionType.PortfolioRevalued,
                summaryText = "עדכון שווי תיק מ-1,000.00 ₪ ל-1,500.00 ₪",
                undoOfOperationId = null,
                amountAgoras = 0L,
                details = emptyList(),
                isUndoable = true,
            ),
        )
        val summaryLine = buildTransactionHistoryCsv(groups, utc).split("\r\n")[1]
        summaryLine shouldBe
            "2026-06-28 10:00,עדכון שווי,\"עדכון שווי תיק מ-1,000.00 ₪ ל-1,500.00 ₪\",,0.00 ₪,,"
    }

    // ── Portfolio snapshot (E9.2) ─────────────────────────────────────────────────

    test("snapshot CSV starts with the Hebrew header row") {
        buildPortfolioSnapshotCsv("p1", "פנסיה", emptyList()) shouldStartWith
            "שם תיק,מזהה תיק,שם קרן,מזהה קרן,יתרה,אחוז הקצאה\r\n"
    }

    test("snapshot row repeats portfolio context, escapes commas, formats allocation to one decimal") {
        val funds = listOf(
            FundListItem(
                fundId = "f1",
                name = "מניות",
                balanceAgoras = 123456L, // 1,234.56 ₪ — quoted
                allocationPercent = 33.33,
                createdAtUtc = instant,
            ),
        )
        // Portfolio name contains a comma → must be quoted on every row.
        val line = buildPortfolioSnapshotCsv("p1", "פנסיה, ראשי", funds).split("\r\n")[1]
        line shouldBe "\"פנסיה, ראשי\",p1,מניות,f1,\"1,234.56 ₪\",33.3%"
    }
})
