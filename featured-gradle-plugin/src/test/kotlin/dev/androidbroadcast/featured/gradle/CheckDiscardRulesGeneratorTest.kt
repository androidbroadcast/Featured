package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckDiscardRulesGeneratorTest {
    // ── empty / no-op cases ──────────────────────────────────────────────────

    @Test
    fun `returns blank for empty entries`() {
        assertTrue(CheckDiscardRulesGenerator.generate(emptyList()).isBlank())
    }

    @Test
    fun `returns blank when all class specs are blank`() {
        val entries =
            listOf(
                CheckDiscardEntry(flagKey = "new_checkout", classSpec = "   "),
                CheckDiscardEntry(flagKey = "promo", classSpec = ""),
            )
        assertTrue(CheckDiscardRulesGenerator.generate(entries).isBlank())
    }

    // ── rule generation ──────────────────────────────────────────────────────

    @Test
    fun `generates a checkdiscard rule for a class spec`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(CheckDiscardEntry("new_checkout", "com.example.checkout.newflow.**")),
            )
        assertContains(rules, "-checkdiscard class com.example.checkout.newflow.** { *; }")
    }

    @Test
    fun `names the guarding flag in a comment`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(CheckDiscardEntry("new_checkout", "com.example.checkout.newflow.**")),
            )
        assertContains(rules, "# guarded by flag: new_checkout")
    }

    @Test
    fun `output starts with comment header`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(CheckDiscardEntry("flag", "com.example.Foo")),
            )
        assertTrue(rules.trimStart().startsWith("#"), "Output must start with a comment")
    }

    @Test
    fun `mentions whyareyoukeeping for diagnostics`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(CheckDiscardEntry("flag", "com.example.Foo")),
            )
        assertContains(rules, "-whyareyoukeeping")
    }

    @Test
    fun `trims surrounding whitespace from the class spec`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(CheckDiscardEntry("flag", "  com.example.Foo  ")),
            )
        assertContains(rules, "-checkdiscard class com.example.Foo { *; }")
        assertFalse(rules.contains("  com.example.Foo  "), "Class spec must be trimmed")
    }

    // ── multiple targets ─────────────────────────────────────────────────────

    @Test
    fun `generates one rule per target`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(
                    CheckDiscardEntry("flag_a", "com.example.a.**"),
                    CheckDiscardEntry("flag_b", "com.example.b.**"),
                ),
            )
        assertEquals(
            2,
            rules.lines().count { it.startsWith("-checkdiscard ") },
            "Expected exactly one -checkdiscard line per target",
        )
        assertContains(rules, "com.example.a.**")
        assertContains(rules, "com.example.b.**")
    }

    @Test
    fun `skips blank specs but keeps valid ones`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(
                    CheckDiscardEntry("flag_a", "com.example.a.**"),
                    CheckDiscardEntry("flag_b", "   "),
                ),
            )
        assertContains(rules, "com.example.a.**")
        assertFalse(rules.contains("flag_b"), "Blank-spec entries must not appear")
    }

    @Test
    fun `output has no trailing newline`() {
        val rules =
            CheckDiscardRulesGenerator.generate(
                listOf(CheckDiscardEntry("flag", "com.example.Foo")),
            )
        assertFalse(rules.endsWith("\n"), "Output must not end with a trailing newline")
    }
}
