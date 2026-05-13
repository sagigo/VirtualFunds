package com.virtualfunds.domain.model

/**
 * Canonical registry of all transaction types (E5.2).
 *
 * The `wireValue` of each entry is the exact string written to / read from the database's
 * `transactions.transaction_type` text column. Both desktop (`TransactionType.cs`) and this
 * Kotlin enum use these identical strings — they must never drift.
 *
 * Conversion helpers ([fromWire], [TransactionType.wireValue]) are the only path between
 * wire strings and the typed enum. Don't `.name` or `.toString()` the enum to build wire
 * values; the names happen to match today, but treating them as authoritative would couple
 * the wire format to Kotlin identifier rules.
 */
enum class TransactionType(val wireValue: String) {
    // ── Portfolio lifecycle ──────────────────────────────────────────────────
    PortfolioCreated("PortfolioCreated"),
    PortfolioRenamed("PortfolioRenamed"),
    PortfolioClosed("PortfolioClosed"),

    // ── Fund lifecycle ───────────────────────────────────────────────────────
    FundCreated("FundCreated"),
    FundRenamed("FundRenamed"),
    FundDeleted("FundDeleted"),

    // ── Deposits / Withdrawals ───────────────────────────────────────────────
    FundDeposit("FundDeposit"),
    FundWithdrawal("FundWithdrawal"),

    // ── Transfers ────────────────────────────────────────────────────────────
    Transfer("Transfer"),
    TransferCredit("TransferCredit"),
    TransferDebit("TransferDebit"),

    // ── Revaluation ──────────────────────────────────────────────────────────
    PortfolioRevalued("PortfolioRevalued"),
    RevaluationCredit("RevaluationCredit"),
    RevaluationDebit("RevaluationDebit"),

    // ── Automation ───────────────────────────────────────────────────────────
    ScheduledDepositExecuted("ScheduledDepositExecuted"),

    // ── Undo ─────────────────────────────────────────────────────────────────
    Undo("Undo"),
    ;

    companion object {
        private val byWire: Map<String, TransactionType> =
            entries.associateBy { it.wireValue }

        /**
         * Parses a wire string into a [TransactionType], or `null` if the value is unknown
         * (e.g. a newer transaction type written by a future server build that this client
         * doesn't recognise). Callers should treat `null` as "skip this row" rather than
         * failing the whole query — defensive forwards-compatibility.
         */
        fun fromWire(value: String): TransactionType? = byWire[value]
    }
}
