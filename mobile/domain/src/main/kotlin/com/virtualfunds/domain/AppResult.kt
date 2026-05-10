package com.virtualfunds.domain

/**
 * Typed result returned by all repository and service functions (mobile/CLAUDE.md §Error Handling).
 *
 * Instead of throwing exceptions (the desktop/C# approach), Kotlin code returns
 * AppResult<T>. The caller uses a `when` expression which the compiler enforces —
 * you cannot ignore a failure branch the way you can forget a try/catch.
 *
 * Usage:
 *   when (val result = repo.getPortfolios()) {
 *       is AppResult.Success -> render(result.value)
 *       is AppResult.Failure -> showError(result.error)
 *   }
 */
sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

/**
 * Structured error type mapping the spec's error tokens (E4.7) to named Kotlin types.
 *
 * The sealed class hierarchy mirrors the ERR_* token prefixes from the spec.
 * Example mapping:  ERR_VALIDATION:EMPTY_NAME  →  AppError.Validation.EmptyName
 */
sealed class AppError {

    sealed class Validation : AppError() {
        /** ERR_VALIDATION:EMPTY_NAME — name was blank after trimming. */
        data object EmptyName : Validation()
        /** ERR_VALIDATION:DUPLICATE_NAME — name already exists (case-insensitive). */
        data object DuplicateName : Validation()
        /** ERR_VALIDATION:PORTFOLIO_CLOSED — mutation attempted on a closed portfolio. */
        data object PortfolioClosed : Validation()
        /** ERR_VALIDATION:FUND_NOT_EMPTY — delete attempted while fund balance > 0. */
        data object FundNotEmpty : Validation()
        /** ERR_VALIDATION:FUND_HAS_ENABLED_SCHEDULED_DEPOSIT — delete blocked by active schedule. */
        data object FundHasEnabledScheduledDeposit : Validation()
        /** ERR_VALIDATION:PORTFOLIO_TOTAL_IS_ZERO — revalue attempted on a zero-total portfolio. */
        data object PortfolioTotalIsZero : Validation()
        /** A validation token the client does not recognise — contains the raw token string. */
        data class Unknown(val token: String) : Validation()
    }

    sealed class Invariant : AppError() {
        /** ERR_INVARIANT:NEGATIVE_BALANCE — operation would drive a fund balance below zero. */
        data object NegativeBalance : Invariant()
        /** ERR_INVARIANT:TOTAL_MISMATCH — server detected a delta computation bug in the client. */
        data object TotalMismatch : Invariant()
    }

    /** ERR_UNAUTHORIZED — user is not authenticated or does not own the resource. */
    data object Unauthorized : AppError()

    /**
     * Authentication-specific errors (E3.4). Used by [com.virtualfunds.domain.auth.AuthRepository].
     * These map to the desktop's typed exceptions (`AuthExceptions.cs`) so error UX is identical.
     */
    sealed class Auth : AppError() {
        /** Email field was empty or whitespace-only. */
        data object EmptyEmail : Auth()
        /** Email field did not contain '@'. */
        data object InvalidEmailFormat : Auth()
        /** Password field was empty or whitespace-only. */
        data object EmptyPassword : Auth()
        /** Password is shorter than [com.virtualfunds.domain.auth.AuthValidator.MIN_PASSWORD_LENGTH]. */
        data object PasswordTooShort : Auth()
        /** Sign-in rejected by Supabase (wrong email or password). */
        data object InvalidCredentials : Auth()
        /** Sign-up rejected by Supabase (e.g. email already registered). */
        data object RegistrationFailed : Auth()
    }

    /** ERR_NOT_FOUND — requested entity does not exist. */
    data class NotFound(val entity: String = "") : AppError()

    /** ERR_CONFLICT — generic concurrency or state conflict (spec E4.7 prefix). */
    data class Conflict(val token: String = "") : AppError()

    /** Network or transport error (not a server-side business error). */
    data class Network(val message: String) : AppError()

    /** Unexpected error that doesn't map to any known token. */
    data class Unknown(val message: String) : AppError()
}
