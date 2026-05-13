package com.virtualfunds.domain.model

import java.time.Instant

/**
 * A user's portfolio — a named collection of funds (E4.3.1).
 *
 * Mirrors `desktop/VirtualFunds.Core/Models/Portfolio.cs`. Pure domain type — no Supabase
 * annotations live here; the wire DTO is `:data` `dto/PortfolioDto`.
 *
 * Soft-deleted portfolios have a non-null [closedAtUtc] and are read-only (E2.7).
 */
data class Portfolio(
    val portfolioId: String,
    val name: String,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
    val closedAtUtc: Instant?,
) {
    val isClosed: Boolean get() = closedAtUtc != null
}

/**
 * Display-oriented portfolio entry for the portfolio list screen.
 *
 * The total is *derived* from `SUM(funds.balance_agoras)` per E5.9 — it is never stored on
 * the portfolios table itself. Mirrors `PortfolioListItem.cs`.
 *
 * The formatted total is provided by the UI layer (Compose calls [formatAgoras]) — we don't
 * expose a computed property here to keep `:domain` free of any UI-specific formatting state.
 */
data class PortfolioListItem(
    val portfolioId: String,
    val name: String,
    val totalBalanceAgoras: Long,
)
