package com.virtualfunds.data.error

import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tests for [RpcErrorMapper].
 *
 * The mapper's job is to turn a [Throwable] — whose message contains a server-side ERR_*
 * token (E4.7) — into a typed [AppError]. This is the boundary between raw Supabase
 * exceptions and the domain error model the UI reasons about.
 *
 * All tests use plain [RuntimeException] with the token in the message because:
 *   1. Token matching runs on the combined message string before any type checks.
 *   2. Constructing real Supabase `RestException` / `HttpRequestException` instances
 *      would require Ktor internals that aren't worth pulling into unit tests.
 *
 * The "no ERR_ token" fallback path is tested separately to verify it produces
 * [AppError.Unknown] (and not a crash) for completely unexpected exceptions.
 */
class RpcErrorMapperTest : FunSpec({

    // ── Validation tokens ────────────────────────────────────────────────────

    test("ERR_VALIDATION:EMPTY_NAME maps to Validation.EmptyName") {
        val ex = RuntimeException("ERR_VALIDATION:EMPTY_NAME")
        RpcErrorMapper.map(ex) shouldBe AppError.Validation.EmptyName
    }

    test("ERR_VALIDATION:DUPLICATE_NAME maps to Validation.DuplicateName") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:DUPLICATE_NAME")) shouldBe
            AppError.Validation.DuplicateName
    }

    test("ERR_VALIDATION:PORTFOLIO_CLOSED maps to Validation.PortfolioClosed") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:PORTFOLIO_CLOSED")) shouldBe
            AppError.Validation.PortfolioClosed
    }

    test("ERR_VALIDATION:FUND_NOT_EMPTY maps to Validation.FundNotEmpty") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:FUND_NOT_EMPTY")) shouldBe
            AppError.Validation.FundNotEmpty
    }

    test("ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT maps correctly") {
        RpcErrorMapper.map(
            RuntimeException("ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT")
        ) shouldBe AppError.Validation.FundHasEnabledScheduledDeposit
    }

    test("ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO maps to Validation.PortfolioTotalIsZero") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO")) shouldBe
            AppError.Validation.PortfolioTotalIsZero
    }

    test("ERR_VALIDATION:NEGATIVE_AMOUNT maps to Validation.NegativeAmount") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:NEGATIVE_AMOUNT")) shouldBe
            AppError.Validation.NegativeAmount
    }

    test("ERR_VALIDATION:INVALID_SCHEDULE_FIELDS maps to Validation.InvalidScheduleFields") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:INVALID_SCHEDULE_FIELDS")) shouldBe
            AppError.Validation.InvalidScheduleFields
    }

    test("ERR_VALIDATION:INVALID_SCHEDULE_KIND maps to Validation.InvalidScheduleKind") {
        RpcErrorMapper.map(RuntimeException("ERR_VALIDATION:INVALID_SCHEDULE_KIND")) shouldBe
            AppError.Validation.InvalidScheduleKind
    }

    // ── Invariant tokens ─────────────────────────────────────────────────────

    test("ERR_INVARIANT:NEGATIVE_BALANCE maps to Invariant.NegativeBalance") {
        RpcErrorMapper.map(RuntimeException("ERR_INVARIANT:NEGATIVE_BALANCE")) shouldBe
            AppError.Invariant.NegativeBalance
    }

    test("ERR_INVARIANT:TOTAL_MISMATCH maps to Invariant.TotalMismatch") {
        RpcErrorMapper.map(RuntimeException("ERR_INVARIANT:TOTAL_MISMATCH")) shouldBe
            AppError.Invariant.TotalMismatch
    }

    // ── Auth / not-found / conflict tokens ───────────────────────────────────

    test("ERR_UNAUTHORIZED maps to Unauthorized") {
        RpcErrorMapper.map(RuntimeException("ERR_UNAUTHORIZED")) shouldBe
            AppError.Unauthorized
    }

    test("ERR_NOT_FOUND maps to NotFound") {
        RpcErrorMapper.map(RuntimeException("ERR_NOT_FOUND")) shouldBe
            AppError.NotFound()
    }

    test("ERR_CONFLICT maps to Conflict") {
        RpcErrorMapper.map(RuntimeException("ERR_CONFLICT")) shouldBe
            AppError.Conflict()
    }

    // ── Unknown ERR_VALIDATION token ──────────────────────────────────────────

    test("unrecognised ERR_VALIDATION token maps to Validation.Unknown with the token") {
        val raw = "ERR_VALIDATION:SOME_FUTURE_RULE"
        val error = RpcErrorMapper.map(RuntimeException(raw))
        error.shouldBeInstanceOf<AppError.Validation.Unknown>()
        (error as AppError.Validation.Unknown).token shouldBe raw
    }

    test("ERR_VALIDATION token with trailing whitespace is still captured") {
        val raw = "ERR_VALIDATION:ANOTHER_RULE "
        val error = RpcErrorMapper.map(RuntimeException(raw))
        error.shouldBeInstanceOf<AppError.Validation.Unknown>()
    }

    // ── Token found only in cause message ────────────────────────────────────

    test("token in cause.message is matched even when not in primary message") {
        // Supabase-kt sometimes wraps the server message inside a cause exception.
        val cause = RuntimeException("detail: ERR_VALIDATION:EMPTY_NAME")
        val wrapper = RuntimeException("RPC call failed", cause)
        RpcErrorMapper.map(wrapper) shouldBe AppError.Validation.EmptyName
    }

    test("token in primary message takes precedence over missing cause") {
        val ex = RuntimeException("ERR_VALIDATION:DUPLICATE_NAME")
        RpcErrorMapper.map(ex) shouldBe AppError.Validation.DuplicateName
    }

    // ── No ERR_ token — fallback to Unknown ──────────────────────────────────

    test("exception with no ERR_ token maps to AppError.Unknown") {
        val error = RpcErrorMapper.map(RuntimeException("Something went badly wrong"))
        error.shouldBeInstanceOf<AppError.Unknown>()
    }

    test("null message exception still maps to AppError.Unknown without crashing") {
        val ex = object : RuntimeException() {
            override val message: String? get() = null
        }
        val error = RpcErrorMapper.map(ex)
        error.shouldBeInstanceOf<AppError.Unknown>()
    }

    // ── runCatchingMapped ─────────────────────────────────────────────────────

    test("runCatchingMapped returns Success when block succeeds") {
        val result = RpcErrorMapper.runCatchingMapped { 42 }
        result shouldBe AppResult.Success(42)
    }

    test("runCatchingMapped returns Failure when block throws") {
        val result = RpcErrorMapper.runCatchingMapped<Int> {
            throw RuntimeException("ERR_VALIDATION:EMPTY_NAME")
        }
        result shouldBe AppResult.Failure(AppError.Validation.EmptyName)
    }

    test("runCatchingMapped with no ERR_ token produces Failure(Unknown)") {
        val result = RpcErrorMapper.runCatchingMapped<Int> {
            throw RuntimeException("connection refused")
        }
        result.shouldBeInstanceOf<AppResult.Failure>()
        (result as AppResult.Failure).error.shouldBeInstanceOf<AppError.Unknown>()
    }
})
