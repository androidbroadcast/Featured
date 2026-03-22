package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProguardRulesGeneratorTest {
    @Test
    fun `generates no rules when entries list is empty`() {
        val rules = ProguardRulesGenerator.generate(emptyList())
        assertTrue(
            rules.isBlank(),
            "Expected blank output for empty entries, got: '$rules'",
        )
    }

    @Test
    fun `generates no rules for boolean flag with defaultValue true`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "feature_enabled", defaultValue = "true", type = "Boolean", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertTrue(
            rules.isBlank(),
            "Expected no rules for flags with defaultValue=true, got: '$rules'",
        )
    }

    @Test
    fun `generates no rules for non-boolean flags`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "timeout", defaultValue = "30", type = "Int", moduleName = ":app"),
                LocalFlagEntry(key = "server_url", defaultValue = "https://example.com", type = "String", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertTrue(
            rules.isBlank(),
            "Expected no rules for non-boolean flags, got: '$rules'",
        )
    }

    @Test
    fun `generates assumevalues rule for boolean flag with defaultValue false`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "dark_mode", defaultValue = "false", type = "Boolean", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertContains(rules, "-assumevalues")
        assertContains(rules, "dark_mode")
        assertFalse(rules.isBlank())
    }

    @Test
    fun `generated rule contains correct class reference`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "new_ui", defaultValue = "false", type = "Boolean", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertContains(rules, "dev.androidbroadcast.featured.ConfigValues")
    }

    @Test
    fun `generates rules only for boolean false flags when mixed entries provided`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "disabled_flag", defaultValue = "false", type = "Boolean", moduleName = ":app"),
                LocalFlagEntry(key = "enabled_flag", defaultValue = "true", type = "Boolean", moduleName = ":app"),
                LocalFlagEntry(key = "timeout", defaultValue = "30", type = "Int", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertContains(rules, "disabled_flag")
        assertFalse(rules.contains("enabled_flag"), "Should not contain rules for true flags")
        assertFalse(rules.contains("timeout"), "Should not contain rules for non-boolean flags")
    }

    @Test
    fun `generates rules for multiple boolean false flags`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "flag_a", defaultValue = "false", type = "Boolean", moduleName = ":core"),
                LocalFlagEntry(key = "flag_b", defaultValue = "false", type = "Boolean", moduleName = ":feature"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertContains(rules, "flag_a")
        assertContains(rules, "flag_b")
    }

    @Test
    fun `generated output is valid proguard rule format`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "my_flag", defaultValue = "false", type = "Boolean", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        // Must start with -assumevalues directive
        assertTrue(
            rules.trimStart().startsWith("-assumevalues"),
            "Generated rules must start with -assumevalues, got: '$rules'",
        )
    }

    @Test
    fun `generates no rules when RemoteFlag entries are absent from input`() {
        // @RemoteFlag params are never scanned into LocalFlagEntry — the scanner
        // only recognises @LocalFlag. Passing an empty list simulates the result
        // of a project that has only @RemoteFlag declarations.
        val rules = ProguardRulesGenerator.generate(emptyList())
        assertTrue(
            rules.isBlank(),
            "Expected no rules when no @LocalFlag entries are present (e.g. only @RemoteFlag), got: '$rules'",
        )
    }

    @Test
    fun `generates rules for boolean false flags from three modules`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "flag_core", defaultValue = "false", type = "Boolean", moduleName = ":core"),
                LocalFlagEntry(key = "flag_feature", defaultValue = "false", type = "Boolean", moduleName = ":feature"),
                LocalFlagEntry(key = "flag_app", defaultValue = "false", type = "Boolean", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertContains(rules, "flag_core")
        assertContains(rules, "flag_feature")
        assertContains(rules, "flag_app")
        assertContains(rules, ":core")
        assertContains(rules, ":feature")
        assertContains(rules, ":app")
        assertFalse(rules.isBlank(), "Expected rules to be generated for all three modules")
    }

    @Test
    fun `generated rule contains return false for the method`() {
        val entries =
            listOf(
                LocalFlagEntry(key = "my_flag", defaultValue = "false", type = "Boolean", moduleName = ":app"),
            )
        val rules = ProguardRulesGenerator.generate(entries)
        assertContains(rules, "return false")
    }
}
