package com.virtualfunds.domain.model

/**
 * Supported schedule kinds for a scheduled deposit (E8.2).
 *
 * Wire format matches the SQL CHECK constraint:
 *   schedule_kind IN ('Daily', 'Weekly', 'Monthly', 'OneTime')
 */
enum class ScheduleKind(val wireValue: String) {
    OneTime("OneTime"),
    Daily("Daily"),
    Weekly("Weekly"),
    Monthly("Monthly"),
    ;

    companion object {
        private val byWire: Map<String, ScheduleKind> =
            entries.associateBy { it.wireValue }

        /** Parses a wire string into a [ScheduleKind], or `null` for unrecognised values. */
        fun fromWire(value: String): ScheduleKind? = byWire[value]
    }
}
