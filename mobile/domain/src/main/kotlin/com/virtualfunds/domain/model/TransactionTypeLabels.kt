package com.virtualfunds.domain.model

/**
 * Hebrew display labels for [TransactionType] and the filterable summary-type list for the
 * history view (E7.6).
 *
 * Mirrors `desktop/VirtualFunds.Core/Utilities/TransactionTypeLabels.cs` word-for-word so both
 * platforms show identical wording (Cross-Platform Consistency, mobile/CLAUDE.md).
 *
 * Why this lives in `:domain` rather than Android `R.string` resources — same reasoning as
 * [com.virtualfunds.domain.error.toHebrewMessage]: the strings are static and unparameterised,
 * the "which summary types are filterable" rule is spec logic (E7.6) best kept next to the
 * labels, and keeping desktop/mobile labels side by side lets them be diffed for parity
 * without bouncing through XML.
 */
object TransactionTypeLabels {

    private val labels: Map<TransactionType, String> = mapOf(
        // Portfolio structural
        TransactionType.PortfolioCreated to "יצירת תיק",
        TransactionType.PortfolioRenamed to "שינוי שם תיק",
        TransactionType.PortfolioClosed to "סגירת תיק",

        // Fund structural
        TransactionType.FundCreated to "יצירת קרן",
        TransactionType.FundRenamed to "שינוי שם קרן",
        TransactionType.FundDeleted to "מחיקת קרן",

        // Fund money operations
        TransactionType.FundDeposit to "הפקדה",
        TransactionType.FundWithdrawal to "משיכה",
        TransactionType.Transfer to "העברה",
        TransactionType.TransferCredit to "העברה — זיכוי",
        TransactionType.TransferDebit to "העברה — חיוב",

        // Revaluation
        TransactionType.PortfolioRevalued to "עדכון שווי",
        TransactionType.RevaluationCredit to "עדכון שווי — זיכוי",
        TransactionType.RevaluationDebit to "עדכון שווי — חיוב",

        // Scheduled
        TransactionType.ScheduledDepositExecuted to "הפקדה מתוזמנת",

        // Undo
        TransactionType.Undo to "ביטול",
    )

    /**
     * Hebrew label for [type]. Falls back to the wire value if a future type is somehow
     * unmapped — same defensive posture as desktop's `GetLabel` (which falls back to the
     * enum name).
     */
    fun label(type: TransactionType): String = labels[type] ?: type.wireValue

    /**
     * Summary-level transaction types offered as filter options (E7.6), in the same order as
     * desktop's `GetFilterOptions`. Detail-only types (TransferCredit/Debit,
     * RevaluationCredit/Debit) are intentionally excluded — the type filter matches on the
     * summary row's `transaction_type`, so those never appear as standalone choices.
     */
    fun filterOptions(): List<TransactionTypeFilter> =
        listOf(
            TransactionType.FundDeposit,
            TransactionType.FundWithdrawal,
            TransactionType.Transfer,
            TransactionType.PortfolioRevalued,
            TransactionType.FundCreated,
            TransactionType.FundRenamed,
            TransactionType.FundDeleted,
            TransactionType.PortfolioCreated,
            TransactionType.PortfolioRenamed,
            TransactionType.PortfolioClosed,
            TransactionType.ScheduledDepositExecuted,
            TransactionType.Undo,
        ).map { TransactionTypeFilter(type = it, label = label(it)) }
}

/**
 * A filterable transaction type for the history type dropdown (E7.6).
 * Mirrors desktop's `TransactionTypeFilter` record.
 *
 * @param type the underlying summary [TransactionType] the filter matches on.
 * @param label the Hebrew label shown in the dropdown (e.g. "הפקדה").
 */
data class TransactionTypeFilter(
    val type: TransactionType,
    val label: String,
)
