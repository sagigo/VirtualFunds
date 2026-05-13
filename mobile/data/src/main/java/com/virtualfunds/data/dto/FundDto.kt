package com.virtualfunds.data.dto

import com.virtualfunds.domain.model.DeletedFund
import com.virtualfunds.domain.model.Fund
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Wire-format row from the `funds` table (E4.3.2). */
@Serializable
data class FundDto(
    @SerialName("fund_id")          val fundId: String,
    @SerialName("portfolio_id")     val portfolioId: String,
    @SerialName("name")             val name: String,
    @SerialName("normalized_name")  val normalizedName: String,
    @SerialName("balance_agoras")   val balanceAgoras: Long,
    @SerialName("created_at_utc")   val createdAtUtc: String,
    @SerialName("updated_at_utc")   val updatedAtUtc: String,
) {
    /** Converts the wire DTO to the pure-domain [Fund], parsing ISO-8601 timestamps to [Instant]. */
    fun toDomain(): Fund = Fund(
        fundId = fundId,
        portfolioId = portfolioId,
        name = name,
        normalizedName = normalizedName,
        balanceAgoras = balanceAgoras,
        createdAtUtc = Instant.parse(createdAtUtc),
        updatedAtUtc = Instant.parse(updatedAtUtc),
    )
}

/** Wire-format row from the `deleted_funds` tombstone table (E4.3.3). */
@Serializable
data class DeletedFundDto(
    @SerialName("deleted_fund_id") val deletedFundId: String,
    @SerialName("portfolio_id")    val portfolioId: String,
    @SerialName("fund_id")         val fundId: String,
    @SerialName("name")            val name: String,
    @SerialName("normalized_name") val normalizedName: String,
    @SerialName("deleted_at_utc")  val deletedAtUtc: String,
) {
    /** Converts the wire DTO to the pure-domain [DeletedFund]. */
    fun toDomain(): DeletedFund = DeletedFund(
        deletedFundId = deletedFundId,
        portfolioId = portfolioId,
        fundId = fundId,
        name = name,
        normalizedName = normalizedName,
        deletedAtUtc = Instant.parse(deletedAtUtc),
    )
}
