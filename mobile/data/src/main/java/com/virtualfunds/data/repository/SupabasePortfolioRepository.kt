package com.virtualfunds.data.repository

import com.virtualfunds.data.dto.ClosePortfolioParams
import com.virtualfunds.data.dto.CreatePortfolioParams
import com.virtualfunds.data.dto.FundBalanceDto
import com.virtualfunds.data.dto.PortfolioDto
import com.virtualfunds.data.dto.RenamePortfolioParams
import com.virtualfunds.data.error.RpcErrorMapper
import com.virtualfunds.data.util.Ids
import com.virtualfunds.domain.AppResult
import com.virtualfunds.domain.model.PortfolioListItem
import com.virtualfunds.domain.repository.PortfolioRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase-backed implementation of [PortfolioRepository] (PR-4, E5.3–E5.5).
 *
 * Mirrors `SupabasePortfolioService.cs`. Reads use Postgrest `from(...).select { ... }`;
 * mutations are RPC calls whose `ERR_*` token responses are mapped to typed [com.virtualfunds.domain.AppError]
 * via [RpcErrorMapper].
 *
 * `@Singleton` + `@Inject`: one instance per process, constructed lazily on first request.
 * Hilt resolves the [SupabaseClient] from `SupabaseModule.provideSupabaseClient`.
 */
// @Singleton tells Hilt to construct exactly one instance and cache it. Every collaborator
// receives the same repository, which is essential so all of them share one SupabaseClient
// (and one HTTP connection pool / Auth session) — see SupabaseModule.provideSupabaseClient.
@Singleton
class SupabasePortfolioRepository
    // @Inject on the constructor tells Hilt to satisfy [client] from the DI graph
    // (SupabaseModule.provideSupabaseClient). We never instantiate this class ourselves —
    // Hilt's generated factory does, on first request.
    @Inject constructor(
    private val client: SupabaseClient,
) : PortfolioRepository {

    override suspend fun getActivePortfolios(): AppResult<List<PortfolioListItem>> =
        RpcErrorMapper.runCatchingMapped {
            // 1. Active portfolios — RLS handles the auth.uid() scoping; we just filter
            //    closed_at_utc IS NULL and sort by normalized_name ascending (E4.6).
            val portfolioRows = client.from("portfolios").select {
                filter {
                    // `filter(col, FilterOperator.IS, null)` becomes `?closed_at_utc=is.null`
                    // — the PostgREST way to express SQL `IS NULL`.
                    filter("closed_at_utc", FilterOperator.IS, "null")
                }
                order(column = "normalized_name", order = Order.ASCENDING)
            }.decodeList<PortfolioDto>()

            if (portfolioRows.isEmpty()) return@runCatchingMapped emptyList()

            // 2. Sum fund balances grouped by portfolio_id. We only need (portfolio_id,
            //    balance_agoras) so we narrow the SELECT to those columns.
            val fundBalances = client.from("funds").select(
                columns = Columns.list("portfolio_id", "balance_agoras")
            ).decodeList<FundBalanceDto>()

            val totalsByPortfolio: Map<String, Long> = fundBalances
                .groupBy(FundBalanceDto::portfolioId) { it.balanceAgoras }
                .mapValues { (_, balances) -> balances.sum() }

            portfolioRows.map { dto ->
                PortfolioListItem(
                    portfolioId = dto.portfolioId,
                    name = dto.name,
                    totalBalanceAgoras = totalsByPortfolio[dto.portfolioId] ?: 0L,
                )
            }
        }

    override suspend fun createPortfolio(name: String): AppResult<String> =
        RpcErrorMapper.runCatchingMapped {
            val operationId = Ids.newOperationId()
            val summaryTransactionId = Ids.newTransactionId()

            // The RPC returns the new portfolio_id as a JSON string. supabase-kt's
            // `decodeAs<String>()` handles the surrounding quotes.
            client.postgrest.rpc(
                "rpc_create_portfolio",
                parameters = CreatePortfolioParams(
                    name = name,
                    operationId = operationId,
                    summaryTransactionId = summaryTransactionId,
                ),
            ).decodeAs<String>()
        }

    override suspend fun renamePortfolio(
        portfolioId: String,
        newName: String,
    ): AppResult<Unit> = RpcErrorMapper.runCatchingMapped {
        client.postgrest.rpc(
            "rpc_rename_portfolio",
            parameters = RenamePortfolioParams(
                portfolioId = portfolioId,
                newName = newName,
                operationId = Ids.newOperationId(),
                summaryTransactionId = Ids.newTransactionId(),
            ),
        )
        Unit
    }

    override suspend fun closePortfolio(portfolioId: String): AppResult<Unit> =
        RpcErrorMapper.runCatchingMapped {
            client.postgrest.rpc(
                "rpc_close_portfolio",
                parameters = ClosePortfolioParams(
                    portfolioId = portfolioId,
                    operationId = Ids.newOperationId(),
                    summaryTransactionId = Ids.newTransactionId(),
                ),
            )
            Unit
        }
}
