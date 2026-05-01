package com.virtualfunds.domain

import java.util.Locale

/**
 * Formats an agora amount as a display string.
 *
 * Rules (shared across desktop and mobile per CLAUDE.md):
 *   1 NIS = 100 agoras. Output format: "1,234.56 ₪"
 *   - Thousands separator: comma
 *   - Always two decimal places
 *   - Shekel symbol (₪) after the number
 *
 * Mirrors MoneyFormatter.cs in VirtualFunds.Core.
 *
 * @param agoras Amount in agoras. May be negative (e.g. deltas in history).
 * @return Formatted string, e.g. "1,234.56 ₪" or "-50.00 ₪".
 */
fun formatAgoras(agoras: Long): String {
    val isNegative = agoras < 0
    // Work with absolute value to avoid sign complications with % operator.
    val absolute = if (isNegative) -agoras else agoras
    val wholePart = absolute / 100
    val agoraPart = absolute % 100
    val sign = if (isNegative) "-" else ""
    // Locale.US guarantees comma as the thousands separator regardless of device locale.
    return "$sign${String.format(Locale.US, "%,d", wholePart)}.${agoraPart.toString().padStart(2, '0')} ₪"
}
