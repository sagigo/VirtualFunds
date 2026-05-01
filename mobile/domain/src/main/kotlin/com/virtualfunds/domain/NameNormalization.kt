package com.virtualfunds.domain

import java.util.Locale

/**
 * Normalizes a portfolio or fund name for uniqueness comparison (E2.4).
 *
 * Rule: trim leading/trailing whitespace, then lowercase.
 * Using Locale.ROOT for locale-independent, stable lowercasing across all devices.
 *
 * Matches the SQL expression: lower(trim(name))
 */
fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)
