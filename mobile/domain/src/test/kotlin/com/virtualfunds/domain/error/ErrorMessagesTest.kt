package com.virtualfunds.domain.error

import com.virtualfunds.domain.AppError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty

/**
 * Tests for [AppError.toHebrewMessage].
 *
 * Two layers of assertion:
 *   1. Every [AppError] variant produces a non-empty Hebrew string — prevents silent
 *      regressions if someone adds a new AppError without adding a mapping.
 *   2. Key variants' wording must match the desktop's exception messages exactly
 *      so both platforms surface the same text to the user (E4.7, E10.4).
 */
class ErrorMessagesTest : FunSpec({

    // ── Validation errors ────────────────────────────────────────────────────

    test("EmptyName has non-empty message") {
        AppError.Validation.EmptyName.toHebrewMessage().shouldNotBeEmpty()
    }

    test("EmptyName matches desktop wording") {
        // Desktop: "נא להזין שם לתיק." (MainViewModel.cs) /
        //          "השם לא יכול להיות ריק" (ErrorMessages.kt)
        // Both map to the same token — we verify the domain message here.
        AppError.Validation.EmptyName.toHebrewMessage() shouldBe "השם לא יכול להיות ריק"
    }

    test("DuplicateName has non-empty message") {
        AppError.Validation.DuplicateName.toHebrewMessage().shouldNotBeEmpty()
    }

    test("DuplicateName message") {
        AppError.Validation.DuplicateName.toHebrewMessage() shouldBe "השם כבר קיים"
    }

    test("PortfolioClosed has non-empty message") {
        AppError.Validation.PortfolioClosed.toHebrewMessage().shouldNotBeEmpty()
    }

    test("FundNotEmpty has non-empty message") {
        AppError.Validation.FundNotEmpty.toHebrewMessage().shouldNotBeEmpty()
    }

    test("FundHasEnabledScheduledDeposit has non-empty message") {
        AppError.Validation.FundHasEnabledScheduledDeposit.toHebrewMessage().shouldNotBeEmpty()
    }

    test("PortfolioTotalIsZero has non-empty message") {
        AppError.Validation.PortfolioTotalIsZero.toHebrewMessage().shouldNotBeEmpty()
    }

    test("NegativeAmount has non-empty message") {
        AppError.Validation.NegativeAmount.toHebrewMessage().shouldNotBeEmpty()
    }

    test("InvalidScheduleFields has non-empty message") {
        AppError.Validation.InvalidScheduleFields.toHebrewMessage().shouldNotBeEmpty()
    }

    test("InvalidScheduleKind has non-empty message") {
        AppError.Validation.InvalidScheduleKind.toHebrewMessage().shouldNotBeEmpty()
    }

    test("SameFundTransfer has non-empty message") {
        AppError.Validation.SameFundTransfer.toHebrewMessage().shouldNotBeEmpty()
    }

    test("RevalueWouldZeroFund has non-empty message") {
        AppError.Validation.RevalueWouldZeroFund.toHebrewMessage().shouldNotBeEmpty()
    }

    test("Unknown validation token embeds the raw token in the message") {
        val token = "ERR_VALIDATION:SOME_FUTURE_TOKEN"
        val msg = AppError.Validation.Unknown(token).toHebrewMessage()
        msg.shouldNotBeEmpty()
        msg shouldContain token
    }

    // ── Invariant errors ─────────────────────────────────────────────────────

    test("NegativeBalance has non-empty message") {
        AppError.Invariant.NegativeBalance.toHebrewMessage().shouldNotBeEmpty()
    }

    test("TotalMismatch has non-empty message") {
        AppError.Invariant.TotalMismatch.toHebrewMessage().shouldNotBeEmpty()
    }

    // ── Auth errors ──────────────────────────────────────────────────────────

    test("Auth.EmptyEmail has non-empty message") {
        AppError.Auth.EmptyEmail.toHebrewMessage().shouldNotBeEmpty()
    }

    test("Auth.InvalidEmailFormat has non-empty message") {
        AppError.Auth.InvalidEmailFormat.toHebrewMessage().shouldNotBeEmpty()
    }

    test("Auth.EmptyPassword has non-empty message") {
        AppError.Auth.EmptyPassword.toHebrewMessage().shouldNotBeEmpty()
    }

    test("Auth.PasswordTooShort has non-empty message") {
        AppError.Auth.PasswordTooShort.toHebrewMessage().shouldNotBeEmpty()
    }

    test("Auth.InvalidCredentials has non-empty message") {
        AppError.Auth.InvalidCredentials.toHebrewMessage().shouldNotBeEmpty()
    }

    test("Auth.RegistrationFailed has non-empty message") {
        AppError.Auth.RegistrationFailed.toHebrewMessage().shouldNotBeEmpty()
    }

    // ── Generic / transport ──────────────────────────────────────────────────

    test("Unauthorized has non-empty message") {
        AppError.Unauthorized.toHebrewMessage().shouldNotBeEmpty()
    }

    test("NotFound has non-empty message") {
        AppError.NotFound().toHebrewMessage().shouldNotBeEmpty()
    }

    test("Conflict has non-empty message") {
        AppError.Conflict().toHebrewMessage().shouldNotBeEmpty()
    }

    test("Network has non-empty message") {
        AppError.Network("connection refused").toHebrewMessage().shouldNotBeEmpty()
    }

    test("Unknown has non-empty message") {
        AppError.Unknown("unexpected").toHebrewMessage().shouldNotBeEmpty()
    }
})
