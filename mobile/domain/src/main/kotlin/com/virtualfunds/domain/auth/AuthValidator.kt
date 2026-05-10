package com.virtualfunds.domain.auth

import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult

/**
 * Local validation for sign-in / sign-up forms (E3.4 — "Validate fields locally").
 *
 * Mirrors the desktop's validation in `SupabaseAuthService.ValidateEmailAndPassword`:
 *   - email: non-empty + must contain '@'
 *   - password: non-empty
 *   - password length >= 6 (sign-up only — sign-in just trusts the server's verdict)
 *
 * Pure logic, no I/O. Lives in :domain so it's unit-testable without an Android device.
 */
object  AuthValidator {

    /** Minimum password length enforced by Supabase Auth (default GoTrue setting). */
    const val MIN_PASSWORD_LENGTH = 6

    /** Validates the inputs of a sign-in form. Does not check minimum password length. */
    fun validateSignIn(email: String, password: String): AppResult<Unit> =
        validate(email, password, validatePasswordLength = false)

    /** Validates the inputs of a sign-up form. Enforces the minimum password length. */
    fun validateSignUp(email: String, password: String): AppResult<Unit> =
        validate(email, password, validatePasswordLength = true)

    private fun validate(
        email: String,
        password: String,
        validatePasswordLength: Boolean
    ): AppResult<Unit> {
        if (email.isBlank()) return AppResult.Failure(AppError.Auth.EmptyEmail)
        if (!email.contains('@')) return AppResult.Failure(AppError.Auth.InvalidEmailFormat)
        if (password.isBlank()) return AppResult.Failure(AppError.Auth.EmptyPassword)
        if (validatePasswordLength && password.length < MIN_PASSWORD_LENGTH) {
            return AppResult.Failure(AppError.Auth.PasswordTooShort)
        }
        return AppResult.Success(Unit)
    }
}
