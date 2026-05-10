package com.virtualfunds.domain.auth

import com.virtualfunds.domain.AppError
import com.virtualfunds.domain.AppResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for AuthValidator — mirrors the validation expectations from the desktop's
 * SupabaseAuthService.ValidateEmailAndPassword and the spec's E3.4 "Validate fields locally".
 *
 * AppResult-based: a `Failure` wraps an `AppError.Auth.*`; we assert on the specific subclass.
 */
class AuthValidatorTest : FunSpec({

    // ---------- sign-in ----------

    test("sign-in: valid email + non-empty password passes (no min length on sign-in)") {
        AuthValidator.validateSignIn("a@b.co", "x") shouldBe AppResult.Success(Unit)
    }

    test("sign-in: empty email fails with EmptyEmail") {
        val result = AuthValidator.validateSignIn("", "password")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.EmptyEmail
    }

    test("sign-in: whitespace-only email fails with EmptyEmail") {
        val result = AuthValidator.validateSignIn("   ", "password")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.EmptyEmail
    }

    test("sign-in: email without @ fails with InvalidEmailFormat") {
        val result = AuthValidator.validateSignIn("notanemail", "password")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.InvalidEmailFormat
    }

    test("sign-in: empty password fails with EmptyPassword") {
        val result = AuthValidator.validateSignIn("a@b.co", "")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.EmptyPassword
    }

    test("sign-in: short password is allowed (server decides)") {
        AuthValidator.validateSignIn("a@b.co", "abc") shouldBe AppResult.Success(Unit)
    }

    // ---------- sign-up ----------

    test("sign-up: valid email + 6-char password passes") {
        AuthValidator.validateSignUp("a@b.co", "abcdef") shouldBe AppResult.Success(Unit)
    }

    test("sign-up: 5-char password fails with PasswordTooShort") {
        val result = AuthValidator.validateSignUp("a@b.co", "abcde")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.PasswordTooShort
    }

    test("sign-up: empty password fails with EmptyPassword (not PasswordTooShort)") {
        // Empty/whitespace is its own error — distinct from "non-empty but too short".
        val result = AuthValidator.validateSignUp("a@b.co", "")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.EmptyPassword
    }

    test("sign-up: empty email fails before password is examined") {
        val result = AuthValidator.validateSignUp("", "abc")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.EmptyEmail
    }

    test("sign-up: invalid email format fails before password is examined") {
        val result = AuthValidator.validateSignUp("noatsign", "abc")
        result.shouldBeInstanceOf<AppResult.Failure>()
        result.error shouldBe AppError.Auth.InvalidEmailFormat
    }
})
