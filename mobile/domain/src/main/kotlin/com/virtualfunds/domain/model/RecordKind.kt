package com.virtualfunds.domain.model

/**
 * Discriminates transaction rows into per-operation summaries and per-fund detail lines (E7.2).
 *
 * The wire format matches the SQL CHECK constraint:
 *   record_kind IN ('Summary', 'Detail')
 */
enum class RecordKind(val wireValue: String) {
    /** One summary row per operation. `fund_id` is NULL on the row. */
    Summary("Summary"),

    /** One detail row per affected fund per operation. `fund_id` is NOT NULL on the row. */
    Detail("Detail"),
    ;

    companion object {
        /** Parses a wire string into a [RecordKind], or `null` for unrecognised values. */
        fun fromWire(value: String): RecordKind? = when (value) {
            "Summary" -> Summary
            "Detail" -> Detail
            else -> null
        }
    }
}
