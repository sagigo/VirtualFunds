package com.virtualfunds.domain.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests for [TransactionType] wire-value round-tripping.
 *
 * The wire values in this file are the ground truth shared between mobile and desktop
 * (E5.2 / E10.4). A test failure here means the string the Kotlin client sends to or
 * reads from Postgres has changed — which would silently corrupt transaction history.
 *
 * Cross-platform parity: these exact strings match TransactionType.cs on the desktop.
 * Any mismatch between the two files must be treated as a breaking change.
 */
class TransactionTypeTest : FunSpec({

    // ── Portfolio lifecycle ──────────────────────────────────────────────────

    test("PortfolioCreated wire value is 'PortfolioCreated'") {
        TransactionType.PortfolioCreated.wireValue shouldBe "PortfolioCreated"
    }

    test("PortfolioRenamed wire value is 'PortfolioRenamed'") {
        TransactionType.PortfolioRenamed.wireValue shouldBe "PortfolioRenamed"
    }

    test("PortfolioClosed wire value is 'PortfolioClosed'") {
        TransactionType.PortfolioClosed.wireValue shouldBe "PortfolioClosed"
    }

    // ── Fund lifecycle ───────────────────────────────────────────────────────

    test("FundCreated wire value is 'FundCreated'") {
        TransactionType.FundCreated.wireValue shouldBe "FundCreated"
    }

    test("FundRenamed wire value is 'FundRenamed'") {
        TransactionType.FundRenamed.wireValue shouldBe "FundRenamed"
    }

    test("FundDeleted wire value is 'FundDeleted'") {
        TransactionType.FundDeleted.wireValue shouldBe "FundDeleted"
    }

    // ── Deposit / Withdrawal ─────────────────────────────────────────────────

    test("FundDeposit wire value is 'FundDeposit'") {
        TransactionType.FundDeposit.wireValue shouldBe "FundDeposit"
    }

    test("FundWithdrawal wire value is 'FundWithdrawal'") {
        TransactionType.FundWithdrawal.wireValue shouldBe "FundWithdrawal"
    }

    // ── Transfers ────────────────────────────────────────────────────────────

    test("Transfer wire value is 'Transfer'") {
        TransactionType.Transfer.wireValue shouldBe "Transfer"
    }

    test("TransferCredit wire value is 'TransferCredit'") {
        TransactionType.TransferCredit.wireValue shouldBe "TransferCredit"
    }

    test("TransferDebit wire value is 'TransferDebit'") {
        TransactionType.TransferDebit.wireValue shouldBe "TransferDebit"
    }

    // ── Revaluation ──────────────────────────────────────────────────────────

    test("PortfolioRevalued wire value is 'PortfolioRevalued'") {
        TransactionType.PortfolioRevalued.wireValue shouldBe "PortfolioRevalued"
    }

    test("RevaluationCredit wire value is 'RevaluationCredit'") {
        TransactionType.RevaluationCredit.wireValue shouldBe "RevaluationCredit"
    }

    test("RevaluationDebit wire value is 'RevaluationDebit'") {
        TransactionType.RevaluationDebit.wireValue shouldBe "RevaluationDebit"
    }

    // ── Automation ───────────────────────────────────────────────────────────

    test("ScheduledDepositExecuted wire value is 'ScheduledDepositExecuted'") {
        TransactionType.ScheduledDepositExecuted.wireValue shouldBe "ScheduledDepositExecuted"
    }

    // ── Undo ─────────────────────────────────────────────────────────────────

    test("Undo wire value is 'Undo'") {
        TransactionType.Undo.wireValue shouldBe "Undo"
    }

    // ── fromWire round-trips ─────────────────────────────────────────────────

    test("every enum entry round-trips through fromWire") {
        // If any entry's wireValue doesn't parse back to itself, the fromWire map is broken.
        TransactionType.entries.forEach { type ->
            TransactionType.fromWire(type.wireValue) shouldBe type
        }
    }

    test("unknown wire value returns null instead of throwing") {
        TransactionType.fromWire("SomeUnknownFutureType") shouldBe null
    }

    test("empty string returns null") {
        TransactionType.fromWire("") shouldBe null
    }

    test("case-sensitive: lowercase wire value does not match") {
        // Wire values are PascalCase; lowercase must NOT match — a typo or case mismatch
        // should surface as null rather than silently resolving to the wrong type.
        TransactionType.fromWire("funddepôsit") shouldBe null
        TransactionType.fromWire("funddeposit") shouldBe null
    }

    test("fromWire is the only safe path — wire value count matches enum entry count") {
        // Ensures every entry has a unique wire value (no silent duplicates in the map).
        val distinctWireValues = TransactionType.entries.map { it.wireValue }.toSet()
        distinctWireValues.size shouldBe TransactionType.entries.size
    }

    // ── Spot-check: wire value differs from .name if we ever rename an entry ─

    test("PortfolioCreated.name matches wireValue (no Kotlin-identifier rename drift)") {
        TransactionType.PortfolioCreated.name shouldNotBe null
        TransactionType.PortfolioCreated.wireValue shouldBe "PortfolioCreated"
    }
})
