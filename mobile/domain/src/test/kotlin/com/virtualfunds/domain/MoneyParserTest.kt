package com.virtualfunds.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [parseShekelInput].
 *
 * Covers each branch of [ShekelParseResult]. Inputs are chosen to match the desktop's
 * `FundCreateDialog.OkButton_Click` parsing rules so a regression on either platform is
 * caught quickly.
 */
class MoneyParserTest : FunSpec({

    test("empty string returns Empty") {
        parseShekelInput("") shouldBe ShekelParseResult.Empty
    }

    test("whitespace-only returns Empty") {
        parseShekelInput("   ") shouldBe ShekelParseResult.Empty
    }

    test("integer shekel amount converts to agoras (×100)") {
        parseShekelInput("150") shouldBe ShekelParseResult.Success(15000L)
    }

    test("decimal with two fractional digits parses exactly") {
        parseShekelInput("150.50") shouldBe ShekelParseResult.Success(15050L)
    }

    test("decimal with one fractional digit parses exactly") {
        parseShekelInput("150.5") shouldBe ShekelParseResult.Success(15050L)
    }

    test("zero parses to zero agoras") {
        parseShekelInput("0") shouldBe ShekelParseResult.Success(0L)
        parseShekelInput("0.00") shouldBe ShekelParseResult.Success(0L)
    }

    test("non-numeric input returns NotANumber") {
        parseShekelInput("abc") shouldBe ShekelParseResult.NotANumber
        parseShekelInput("12.5.3") shouldBe ShekelParseResult.NotANumber
    }

    test("negative input returns Negative") {
        parseShekelInput("-5") shouldBe ShekelParseResult.Negative
        parseShekelInput("-0.01") shouldBe ShekelParseResult.Negative
    }

    test("more than two decimal places returns TooManyDecimals") {
        parseShekelInput("1.234") shouldBe ShekelParseResult.TooManyDecimals
        parseShekelInput("0.001") shouldBe ShekelParseResult.TooManyDecimals
    }

    test("leading and trailing whitespace are trimmed") {
        parseShekelInput("  42.50  ") shouldBe ShekelParseResult.Success(4250L)
    }

    test("large values up to billions of shekels still fit in Long agoras") {
        // 10 billion shekels = 1,000,000,000,000 agoras, well under Long.MAX_VALUE.
        parseShekelInput("10000000000") shouldBe ShekelParseResult.Success(1_000_000_000_000L)
    }
})
