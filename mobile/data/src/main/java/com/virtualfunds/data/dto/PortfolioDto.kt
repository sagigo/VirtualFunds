package com.virtualfunds.data.dto

import com.virtualfunds.domain.model.Portfolio
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Wire-format representation of a row in the `portfolios` table (E4.3.1).
 *
 * Why a separate DTO instead of putting `@Serializable` on the domain model:
 *   1. Keeps `:domain` pure Kotlin — no `kotlinx.serialization` dependency leaks into it.
 *   2. Lets us pick wire types independently of domain types (here: `String` for
 *      timestamps so we can use the SDK's default Json without registering an Instant
 *      serializer module). The mapping fn parses to `Instant` on the way in.
 *   3. Decouples the DB column names from the Kotlin property names via `@SerialName`,
 *      so renaming a domain field won't break the wire format.
 *
 * `@SerialName` tells kotlinx-serialization to use the exact wire key. Without it, the
 * generated serializer would use the Kotlin property name and our snake_case column names
 * would fail to bind.
 */
@Serializable
data class PortfolioDto(
    @SerialName("portfolio_id") val portfolioId: String,
    @SerialName("name")          val name: String,
    @SerialName("created_at_utc") val createdAtUtc: String,
    @SerialName("updated_at_utc") val updatedAtUtc: String,
    @SerialName("closed_at_utc")  val closedAtUtc: String? = null,
) {
    /** Converts the wire DTO to the pure-domain [Portfolio], parsing ISO-8601 timestamps to [Instant]. */
    fun toDomain(): Portfolio = Portfolio(
        portfolioId = portfolioId,
        name = name,
        createdAtUtc = Instant.parse(createdAtUtc),
        updatedAtUtc = Instant.parse(updatedAtUtc),
        closedAtUtc = closedAtUtc?.let(Instant::parse),
    )
}

/**
 * Minimal projection for reading just per-fund balances when computing portfolio totals.
 * Mirrors `FundBalanceRow` in `SupabasePortfolioService.cs`.
 */
@Serializable
data class FundBalanceDto(
    @SerialName("portfolio_id") val portfolioId: String,
    @SerialName("balance_agoras") val balanceAgoras: Long,
)
