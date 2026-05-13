package com.virtualfunds.domain.model

import java.time.Instant

/**
 * A fund — a named ownership bucket inside a portfolio (E4.3.2).
 *
 * Mirrors `Fund.cs`. Pure domain type, no Supabase annotations (see `:data` `FundDto`).
 *
 * The `allocation_percent` field is *derived* (E5.9, E2.5) and lives only on [FundListItem],
 * not on this base type.
 */
data class Fund(
    val fundId: String,
    val portfolioId: String,
    val name: String,
    val normalizedName: String,
    val balanceAgoras: Long,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)

/**
 * Display-oriented fund entry for the fund list screen (PR-5, E5.9–E5.10).
 *
 * The allocation percent is derived client-side: `balance / total * 100`, or 0 when the
 * portfolio total is 0 (E5.9). Computed once at load time — the UI does not recompute it
 * as it scrolls.
 *
 * Mirrors `FundListItem.cs`. Format strings are produced by `:domain` `formatAgoras` /
 * UI code; we don't bake them into the data class.
 */
data class FundListItem(
    val fundId: String,
    val name: String,
    val balanceAgoras: Long,
    val allocationPercent: Double,
    val createdAtUtc: Instant,
)

/**
 * Tombstone record preserving a fund's name after deletion (E4.3.3), used for history
 * rendering (E7.7). Mirrors `DeletedFund.cs`.
 */
data class DeletedFund(
    val deletedFundId: String,
    val portfolioId: String,
    val fundId: String,
    val name: String,
    val normalizedName: String,
    val deletedAtUtc: Instant,
)
