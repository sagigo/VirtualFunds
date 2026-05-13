package com.virtualfunds.data.error

import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException

/**
 * Translates Supabase SDK exceptions into [AppError]s by matching the spec's `ERR_*`
 * tokens (E4.7) inside the message body.
 *
 * Mirrors the `ThrowForRpcError` switch in each desktop service. We centralise it here
 * rather than duplicating per-repository because the token set is shared (most repos can
 * raise NotFound, PortfolioClosed, etc.) and it keeps repository code uniformly thin.
 *
 * Why string-matching: PL/pgSQL `RAISE EXCEPTION 'ERR_VALIDATION:DUPLICATE_NAME'` surfaces
 * as a 4xx response from PostgREST whose body includes the token verbatim. supabase-kt
 * surfaces this as [RestException] (or its [io.github.jan.supabase.postgrest.exception.PostgrestRestException]
 * subclass). There is no structured code field, so substring matching is the contract — the
 * same approach the desktop client uses.
 */
internal object RpcErrorMapper {

    /**
     * Maps an arbitrary [Throwable] caught inside a repository call to an [AppError].
     * `null` is never returned — every input produces something the UI can render.
     */
    fun map(throwable: Throwable): AppError {
        // Combine message + cause message so we catch tokens wherever the SDK puts them.
        val haystack = buildString {
            throwable.message?.let { append(it) }
            throwable.cause?.message?.let { append('\n').append(it) }
        }

        // Order matters slightly: match the more specific tokens first to avoid
        // ERR_VALIDATION matching before its colon-suffixed variant.
        return when {
            haystack.contains("ERR_VALIDATION:EMPTY_NAME") -> AppError.Validation.EmptyName
            haystack.contains("ERR_VALIDATION:DUPLICATE_NAME") -> AppError.Validation.DuplicateName
            haystack.contains("ERR_VALIDATION:PORTFOLIO_CLOSED") -> AppError.Validation.PortfolioClosed
            haystack.contains("ERR_VALIDATION:FUND_NOT_EMPTY") -> AppError.Validation.FundNotEmpty
            haystack.contains("ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT") ->
                AppError.Validation.FundHasEnabledScheduledDeposit
            haystack.contains("ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO") ->
                AppError.Validation.PortfolioTotalIsZero
            haystack.contains("ERR_VALIDATION:NEGATIVE_AMOUNT") -> AppError.Validation.NegativeAmount
            haystack.contains("ERR_VALIDATION:INVALID_SCHEDULE_FIELDS") ->
                AppError.Validation.InvalidScheduleFields
            haystack.contains("ERR_VALIDATION:INVALID_SCHEDULE_KIND") ->
                AppError.Validation.InvalidScheduleKind

            haystack.contains("ERR_INVARIANT:NEGATIVE_BALANCE") -> AppError.Invariant.NegativeBalance
            haystack.contains("ERR_INVARIANT:TOTAL_MISMATCH") -> AppError.Invariant.TotalMismatch

            haystack.contains("ERR_UNAUTHORIZED") -> AppError.Unauthorized
            haystack.contains("ERR_NOT_FOUND") -> AppError.NotFound()
            haystack.contains("ERR_CONFLICT") -> AppError.Conflict()

            // Unknown ERR_VALIDATION token — preserve the original for diagnostics.
            haystack.contains("ERR_VALIDATION:") ->
                AppError.Validation.Unknown(extractToken(haystack, "ERR_VALIDATION:"))

            // No ERR_ token at all — categorize by exception type.
            throwable is HttpRequestException ->
                AppError.Network(throwable.message ?: "network error")
            throwable is RestException ->
                AppError.Unknown(throwable.message ?: "rest error")
            else ->
                AppError.Unknown(throwable.message ?: throwable::class.simpleName ?: "unknown")
        }
    }

    /**
     * Convenience: catches any exception thrown by [block] and returns the matching
     * [AppResult]. Keeps repository methods to a single try/catch line.
     */
    inline fun <T> runCatchingMapped(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (t: Throwable) {
        AppResult.Failure(map(t))
    }

    /**
     * Pulls the part of the token after the prefix up to the next whitespace, comma,
     * or quote — best-effort, used only to fill in [AppError.Validation.Unknown.token].
     */
    private fun extractToken(haystack: String, prefix: String): String {
        val start = haystack.indexOf(prefix)
        if (start < 0) return prefix
        val end = haystack.indexOfAny(charArrayOf(' ', '\n', ',', '"', '\''), startIndex = start)
        return haystack.substring(start, if (end < 0) haystack.length else end)
    }
}
