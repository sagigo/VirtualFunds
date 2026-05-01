package com.virtualfunds.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Unit tests for normalizeName — verifies the spec rule (E2.4):
 * trim leading/trailing whitespace, then lowercase using a stable normalization rule.
 */
class NameNormalizationTest : FunSpec({

    test("simple lowercase pass-through") {
        normalizeName("hello") shouldBe "hello"
    }

    test("uppercase is lowercased") {
        normalizeName("HELLO") shouldBe "hello"
    }

    test("mixed case is lowercased") {
        normalizeName("HeLLo WoRLD") shouldBe "hello world"
    }

    test("leading and trailing whitespace is trimmed") {
        normalizeName("   hello   ") shouldBe "hello"
    }

    test("internal whitespace is preserved") {
        normalizeName("  hello world  ") shouldBe "hello world"
    }

    test("Hebrew text passes through unchanged (no case in Hebrew)") {
        normalizeName("ילדים") shouldBe "ילדים"
    }

    test("Hebrew with whitespace is trimmed") {
        normalizeName("  קרן ילדים  ") shouldBe "קרן ילדים"
    }

    test("empty string stays empty") {
        normalizeName("") shouldBe ""
    }

    test("whitespace-only string becomes empty") {
        normalizeName("   ") shouldBe ""
    }

    test("Turkish dotted I uses Locale.ROOT (not locale-dependent)") {
        // In Turkish locale, 'I'.lowercase() would yield 'ı' (dotless), not 'i'.
        // We use Locale.ROOT so this stays "i" everywhere — required for cross-platform stability.
        normalizeName("I") shouldBe "i"
    }
})
