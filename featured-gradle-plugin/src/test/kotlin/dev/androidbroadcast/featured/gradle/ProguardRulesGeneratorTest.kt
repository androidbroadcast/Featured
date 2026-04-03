package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProguardRulesGeneratorTest {
    private val modulePath = ":feature:ui"
    private val expectedClass =
        "dev.androidbroadcast.featured.generated.${ExtensionFunctionGenerator.jvmFileName(modulePath)}"

    // ── empty / no-op cases ──────────────────────────────────────────────────

    @Test
    fun `returns blank for empty entries`() {
        assertTrue(ProguardRulesGenerator.generate(emptyList(), modulePath).isBlank())
    }

    @Test
    fun `returns blank when all entries are remote`() {
        val entries =
            listOf(
                entry("promo", "false", "Boolean", flagType = LocalFlagEntry.FLAG_TYPE_REMOTE),
            )
        assertTrue(ProguardRulesGenerator.generate(entries, modulePath).isBlank())
    }

    // ── per-function rule generation ─────────────────────────────────────────

    @Test
    fun `generates assumevalues block for local boolean false flag`() {
        val entries = listOf(entry("dark_mode", "false", "Boolean"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "-assumevalues")
        assertContains(rules, "boolean isDarkModeEnabled")
        assertContains(rules, "return false")
    }

    @Test
    fun `generates assumevalues block for local boolean true flag`() {
        val entries = listOf(entry("main_button_red", "true", "Boolean"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "boolean isMainButtonRedEnabled")
        assertContains(rules, "return true")
    }

    @Test
    fun `targets the generated extensions class not ConfigValues`() {
        val entries = listOf(entry("dark_mode", "false", "Boolean"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, expectedClass)
        assertFalse(rules.contains("ConfigValues {"), "Must not target ConfigValues directly")
    }

    @Test
    fun `generates int rule with correct jvm type`() {
        val entries = listOf(entry("max_retries", "3", "Int"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "int getMaxRetries")
        assertContains(rules, "return 3")
    }

    @Test
    fun `generates long rule with long suffix stripped`() {
        val entries = listOf(entry("timeout_ms", "5000L", "Long"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "long getTimeoutMs")
        assertContains(rules, "return 5000")
        assertFalse(rules.contains("return 5000L"), "Long literal suffix must be stripped")
    }

    @Test
    fun `generates float rule with float suffix stripped`() {
        val entries = listOf(entry("threshold", "0.5f", "Float"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "float getThreshold")
        assertContains(rules, "return 0.5")
    }

    @Test
    fun `generates double rule`() {
        val entries = listOf(entry("ratio", "1.5", "Double"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "double getRatio")
        assertContains(rules, "return 1.5")
    }

    @Test
    fun `generates string rule with quoted value`() {
        val entries = listOf(entry("api_url", "\"https://example.com\"", "String"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "java.lang.String getApiUrl")
        assertContains(rules, "return \"https://example.com\"")
    }

    // ── multiple flags ───────────────────────────────────────────────────────

    @Test
    fun `generates one rule per local flag`() {
        val entries =
            listOf(
                entry("flag_a", "false", "Boolean"),
                entry("flag_b", "true", "Boolean"),
                entry("retry_count", "3", "Int"),
            )
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "isFlagAEnabled")
        assertContains(rules, "isFlagBEnabled")
        assertContains(rules, "getRetryCount")
    }

    @Test
    fun `excludes remote flags from rules`() {
        val entries =
            listOf(
                entry("local_flag", "false", "Boolean", flagType = LocalFlagEntry.FLAG_TYPE_LOCAL),
                entry("remote_flag", "false", "Boolean", flagType = LocalFlagEntry.FLAG_TYPE_REMOTE),
            )
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "isLocalFlagEnabled")
        assertFalse(rules.contains("isRemoteFlagEnabled"), "Remote flags must not appear in ProGuard rules")
    }

    // ── module-path-based class name ─────────────────────────────────────────

    @Test
    fun `class name differs per module path`() {
        val entries = listOf(entry("flag", "false", "Boolean"))
        val rulesApp = ProguardRulesGenerator.generate(entries, ":app")
        val rulesFeature = ProguardRulesGenerator.generate(entries, ":feature:checkout")
        assertFalse(
            rulesApp == rulesFeature,
            "Different module paths must produce different class names",
        )
    }

    // ── output format ────────────────────────────────────────────────────────

    @Test
    fun `output starts with comment header`() {
        val entries = listOf(entry("flag", "false", "Boolean"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertTrue(rules.trimStart().startsWith("#"), "Output must start with a comment")
    }

    @Test
    fun `each method rule includes ConfigValues parameter type`() {
        val entries = listOf(entry("dark_mode", "false", "Boolean"))
        val rules = ProguardRulesGenerator.generate(entries, modulePath)
        assertContains(rules, "dev.androidbroadcast.featured.ConfigValues")
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun entry(
        key: String,
        default: String,
        type: String,
        flagType: String = LocalFlagEntry.FLAG_TYPE_LOCAL,
    ) = LocalFlagEntry(
        key = key,
        defaultValue = default,
        type = type,
        moduleName = modulePath,
        propertyName = key.toCamelCase(),
        flagType = flagType,
    )
}
