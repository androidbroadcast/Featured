package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigParamGeneratorTest {
    private val modulePath = ":app"

    // ── local flags ───────────────────────────────────────────────────────────

    @Test
    fun `generates module-suffixed local flags object for local boolean flag`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "object GeneratedLocalFlagsApp")
        assertContains(local, "val darkMode = ConfigParam<Boolean>")
    }

    @Test
    fun `generated local ConfigParam uses named key argument`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "key = \"dark_mode\"")
        assertContains(local, "defaultValue = false")
    }

    @Test
    fun `generated local ConfigParam includes description when present`() {
        val entry = localEntry("dark_mode", "false", "Boolean").copy(description = "Enable dark mode")
        val (local, _) = ConfigParamGenerator.generate(listOf(entry), modulePath)
        assertContains(local, "description = \"Enable dark mode\"")
    }

    @Test
    fun `generated local ConfigParam includes category when present`() {
        val entry = localEntry("dark_mode", "false", "Boolean").copy(category = "UI")
        val (local, _) = ConfigParamGenerator.generate(listOf(entry), modulePath)
        assertContains(local, "category = \"UI\"")
    }

    @Test
    fun `generated local ConfigParam omits null description`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertTrue(!local.contains("description ="), "Null description must not appear in output")
    }

    @Test
    fun `local object is internal`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "internal object GeneratedLocalFlagsApp")
    }

    @Test
    fun `local properties do not have explicit public modifier`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertTrue(!local.contains("public val "), "Property declarations must not carry explicit 'public' modifier")
    }

    @Test
    fun `formats Long default with L suffix`() {
        val entries = listOf(localEntry("timeout", "5000L", "Long"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "defaultValue = 5000L")
    }

    @Test
    fun `formats Float default with f suffix`() {
        val entries = listOf(localEntry("ratio", "0.5f", "Float"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "defaultValue = 0.5f")
    }

    @Test
    fun `passes String default as-is (already quoted)`() {
        val entries = listOf(localEntry("url", "\"https://x.com\"", "String"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "defaultValue = \"https://x.com\"")
    }

    // ── remote flags ──────────────────────────────────────────────────────────

    @Test
    fun `generates module-suffixed remote flags object for remote flag`() {
        val entries = listOf(remoteEntry("promo_banner", "false", "Boolean"))
        val (_, remote) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(remote, "object GeneratedRemoteFlagsApp")
        assertContains(remote, "val promoBanner = ConfigParam<Boolean>")
    }

    @Test
    fun `remote object is internal`() {
        val entries = listOf(remoteEntry("promo", "false", "Boolean"))
        val (_, remote) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(remote, "internal object GeneratedRemoteFlagsApp")
    }

    // ── empty cases ───────────────────────────────────────────────────────────

    @Test
    fun `returns empty string for local when no local flags`() {
        val entries = listOf(remoteEntry("promo", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertTrue(local.isEmpty(), "Expected empty local source when no local flags")
    }

    @Test
    fun `returns empty string for remote when no remote flags`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (_, remote) = ConfigParamGenerator.generate(entries, modulePath)
        assertTrue(remote.isEmpty(), "Expected empty remote source when no remote flags")
    }

    @Test
    fun `both empty for empty entries list`() {
        val (local, remote) = ConfigParamGenerator.generate(emptyList(), modulePath)
        assertEquals("", local)
        assertEquals("", remote)
    }

    // ── imports ───────────────────────────────────────────────────────────────

    @Test
    fun `generated local file imports ConfigParam`() {
        val entries = listOf(localEntry("flag", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "import dev.androidbroadcast.featured.ConfigParam")
    }

    @Test
    fun `generated file has auto-generated comment`() {
        val entries = listOf(localEntry("flag", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "Auto-generated by Featured Gradle Plugin")
    }

    // ── module-derived naming ─────────────────────────────────────────────────

    @Test
    fun `different modules produce different object names`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (localA, _) = ConfigParamGenerator.generate(entries, ":feature:checkout")
        val (localB, _) = ConfigParamGenerator.generate(entries, ":feature:ui")
        assertContains(localA, "object GeneratedLocalFlagsFeatureCheckout")
        assertContains(localB, "object GeneratedLocalFlagsFeatureUi")
    }

    @Test
    fun `hyphenated module segment produces valid object name`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, ":sample:feature-checkout")
        assertContains(local, "object GeneratedLocalFlagsSampleFeatureCheckout")
    }

    @Test
    fun `localFileName uses module suffix`() {
        assertEquals("GeneratedLocalFlagsSampleFeatureCheckout.kt", ConfigParamGenerator.localFileName(":sample:feature-checkout"))
    }

    @Test
    fun `remoteFileName uses module suffix`() {
        assertEquals("GeneratedRemoteFlagsSampleFeaturePromotions.kt", ConfigParamGenerator.remoteFileName(":sample:feature-promotions"))
    }

    // ── enum flags ────────────────────────────────────────────────────────────

    @Test
    fun `generates enum ConfigParam with fqn type argument`() {
        val entries = listOf(localEntry("checkout_variant", "LEGACY", "com.example.CheckoutVariant"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "ConfigParam<com.example.CheckoutVariant>")
    }

    @Test
    fun `enum default value uses fqn dot constant syntax`() {
        val entries = listOf(localEntry("checkout_variant", "LEGACY", "com.example.CheckoutVariant"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "defaultValue = com.example.CheckoutVariant.LEGACY")
    }

    @Test
    fun `enum flag is included in local object`() {
        val entries = listOf(localEntry("checkout_variant", "LEGACY", "com.example.CheckoutVariant"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "val checkoutVariant = ConfigParam<com.example.CheckoutVariant>")
    }

    @Test
    fun `enum flag emits enumConstants with kotlin enumEntries call`() {
        val entries = listOf(localEntry("checkout_variant", "LEGACY", "com.example.CheckoutVariant"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertContains(local, "enumConstants = kotlin.enums.enumEntries<com.example.CheckoutVariant>()")
    }

    @Test
    fun `non-enum flag does not emit enumConstants`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries, modulePath)
        assertTrue(!local.contains("enumConstants ="), "Non-enum flag must not emit enumConstants")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun localEntry(
        key: String,
        default: String,
        type: String,
    ) = LocalFlagEntry(
        key = key,
        defaultValue = default,
        type = type,
        moduleName = modulePath,
        propertyName = key.toCamelCase(),
        flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
    )

    private fun remoteEntry(
        key: String,
        default: String,
        type: String,
    ) = LocalFlagEntry(
        key = key,
        defaultValue = default,
        type = type,
        moduleName = modulePath,
        propertyName = key.toCamelCase(),
        flagType = LocalFlagEntry.FLAG_TYPE_REMOTE,
    )
}
