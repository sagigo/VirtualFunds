package com.virtualfunds.ui

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose routes (mobile/CLAUDE.md §Navigation).
 *
 * In Navigation 2.8+ a destination is identified by a `@Serializable` Kotlin type instead of a
 * string template. Arguments are constructor parameters, not string placeholders — the
 * compiler enforces argument types at the call site. The `kotlin-serialization` plugin
 * (applied in `:app`'s build.gradle.kts) generates a `KSerializer<Routes.*>` for each route
 * type at compile time; Navigation Compose uses that serializer to encode/decode the
 * arguments into the back stack.
 *
 * Why a `Routes` namespace object instead of top-level types: groups all destinations in one
 * place so a new screen always shows up here when someone greps for "Routes.".
 */
object Routes {

    /** The portfolio list — the home screen after sign-in. No arguments. */
    @Serializable
    data object PortfolioList

    /**
     * The fund list inside a specific portfolio (PR-5, E5.6–E5.10).
     *
     * @param portfolioId UUID string identifying the portfolio whose funds are shown.
     * @param portfolioName Human-readable name displayed in the top bar. Passed alongside the id
     *   so the screen can render immediately on push, without a separate "load portfolio"
     *   round-trip just to learn the title.
     */
    @Serializable
    data class FundList(
        val portfolioId: String,
        val portfolioName: String,
    )

    /**
     * The immutable transaction history for a specific portfolio (PR-7, E7.6).
     *
     * @param portfolioId UUID string identifying the portfolio whose history is shown.
     * @param portfolioName Human-readable name shown in the top bar, passed alongside the id so
     *   the screen renders its title immediately without an extra "load portfolio" round-trip.
     */
    @Serializable
    data class TransactionHistory(
        val portfolioId: String,
        val portfolioName: String,
    )

    /**
     * Scheduled-deposit management for a specific portfolio (PR-8, E8).
     *
     * @param portfolioId UUID string identifying the portfolio whose scheduled deposits are managed.
     * @param portfolioName Human-readable name shown in the top bar, passed alongside the id so the
     *   screen renders its title immediately without an extra "load portfolio" round-trip.
     */
    @Serializable
    data class ScheduledDeposits(
        val portfolioId: String,
        val portfolioName: String,
    )
}
