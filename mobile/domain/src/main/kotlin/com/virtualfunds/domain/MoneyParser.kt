package com.virtualfunds.domain

/**
 * Outcome of parsing a user-entered shekel amount into integer agoras.
 *
 * Mirrors the desktop's `FundCreateDialog` parsing rules so the same inputs produce the same
 * outcome on both platforms:
 *   - Empty / whitespace → [Empty] (caller decides what that means; fund-create treats it
 *     as "no initial amount" = 0 agoras).
 *   - Cannot parse as a number → [NotANumber].
 *   - Negative → [Negative].
 *   - More than 2 decimal places → [TooManyDecimals].
 *   - Otherwise → [Success] with the integer agoras value.
 */
sealed class ShekelParseResult {
    data object Empty : ShekelParseResult()
    data class Success(val agoras: Long) : ShekelParseResult()
    data object NotANumber : ShekelParseResult()
    data object Negative : ShekelParseResult()
    data object TooManyDecimals : ShekelParseResult()
}

/**
 * Parses a user-typed shekel amount (e.g. `"150.50"`, `"150"`, `""`) into integer agoras.
 *
 * Parsing is done with **pure integer arithmetic** — no `Double`, `Float`, or `BigDecimal`
 * appears anywhere in the math, per the project's "no floating point or decimal for money"
 * invariant in `CLAUDE.md`. The string is split on `.` and each half is parsed as `Long`;
 * the result is `wholePart * 100 + agoraPart` after padding/validating the fractional digits.
 *
 * Acceptable forms:
 *   - integer or decimal with up to two fractional digits (`150`, `150.5`, `150.50`)
 *   - leading/trailing whitespace is ignored
 *   - empty string → [ShekelParseResult.Empty]
 *
 * Decimal separator is `.` only — the input field is rendered LTR so the user types the
 * same characters as on desktop. We don't accept locale-specific separators here to avoid
 * ambiguity between thousands-comma and decimal-comma.
 */
fun parseShekelInput(text: String): ShekelParseResult {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ShekelParseResult.Empty

    val isNegative = trimmed.startsWith('-')
    val body = if (isNegative) trimmed.substring(1) else trimmed

    if (body.isEmpty()) return ShekelParseResult.NotANumber

    val dotIndex = body.indexOf('.')
    val wholePartStr: String
    val fractionPartStr: String
    if (dotIndex < 0) {
        wholePartStr = body
        fractionPartStr = ""
    } else {
        // A second dot — or any non-digit character — fails the digit check below.
        wholePartStr = body.substring(0, dotIndex)
        fractionPartStr = body.substring(dotIndex + 1)
    }

    // Each half must consist only of digits. Empty whole part ("." or ".5") is allowed by
    // BigDecimal but rejected here to keep the input strict and explicit.
    if (wholePartStr.isEmpty() || !wholePartStr.all(Char::isDigit)) return ShekelParseResult.NotANumber
    if (fractionPartStr.any { !it.isDigit() }) return ShekelParseResult.NotANumber
    if (fractionPartStr.length > 2) return ShekelParseResult.TooManyDecimals

    val whole = wholePartStr.toLongOrNull() ?: return ShekelParseResult.NotANumber

    // Pad fractional part to exactly two digits ("5" → "50", "" → "00") so it represents
    // the agora component directly.
    val padded = fractionPartStr.padEnd(2, '0')
    val agoraPart = padded.toLongOrNull() ?: return ShekelParseResult.NotANumber

    val magnitude = whole * 100 + agoraPart
    val signed = if (isNegative) -magnitude else magnitude

    if (signed < 0) return ShekelParseResult.Negative
    return ShekelParseResult.Success(signed)
}
