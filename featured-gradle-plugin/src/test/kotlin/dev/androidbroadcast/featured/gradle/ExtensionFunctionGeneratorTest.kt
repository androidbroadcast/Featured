package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionFunctionGeneratorTest {
    private val modulePath = ":feature:checkout"

    // ── generated file names ──────────────────────────────────────────────────

    @Test
    fun `localFileName for app module`() {
        val name = ExtensionFunctionGenerator.localFileName(":app")
        assertEquals("GeneratedLocalFlagExtensionsApp.kt", name)
    }

    @Test
    fun `remoteFileName for app module`() {
        val name = ExtensionFunctionGenerator.remoteFileName(":app")
        assertEquals("GeneratedRemoteFlagExtensionsApp.kt", name)
    }

    @Test
    fun `fileName for nested module`() {
        val name = ExtensionFunctionGenerator.localFileName(":feature:checkout")
        assertContains(name, "FeatureCheckout")
    }

    @Test
    fun `fileName for hyphenated module segment`() {
        val name = ExtensionFunctionGenerator.localFileName(":sample:feature-checkout")
        assertContains(name, "SampleFeatureCheckout")
        assertFalse(name.contains("-"), "File name must not contain hyphens")
    }

    @Test
    fun `fileName for different modules are distinct`() {
        val a = ExtensionFunctionGenerator.localFileName(":feature:checkout")
        val b = ExtensionFunctionGenerator.localFileName(":feature:ui")
        assertFalse(a == b, "Different modules must produce distinct file names")
    }

    // ── empty input ───────────────────────────────────────────────────────────

    @Test
    fun `returns empty strings for empty entries`() {
        val (local, remote) = ExtensionFunctionGenerator.generate(emptyList(), modulePath)
        assertTrue(local.isEmpty())
        assertTrue(remote.isEmpty())
    }

    @Test
    fun `local source is empty when only remote flags are declared`() {
        val (local, remote) = ExtensionFunctionGenerator.generate(listOf(remoteEntry("promo", "Boolean")), modulePath)
        assertTrue(local.isEmpty())
        assertTrue(remote.isNotEmpty())
    }

    @Test
    fun `remote source is empty when only local flags are declared`() {
        val (local, remote) = ExtensionFunctionGenerator.generate(listOf(localEntry("flag", "Boolean")), modulePath)
        assertTrue(local.isNotEmpty())
        assertTrue(remote.isEmpty())
    }

    // ── local boolean flag ────────────────────────────────────────────────────

    @Test
    fun `generates is…Enabled extension for local boolean flag`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "fun ConfigValues.isDarkModeEnabled(): Boolean")
    }

    @Test
    fun `local boolean extension returns raw value via getValueCached`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "getValueCached(GeneratedLocalFlagsFeatureCheckout.darkMode).value")
    }

    @Test
    fun `local boolean extension is internal non-suspend`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "internal fun ConfigValues.isDarkModeEnabled()")
        assertFalse(local.contains("suspend fun ConfigValues.isDarkModeEnabled()"), "Must not emit suspend modifier")
    }

    // ── local non-boolean flag ────────────────────────────────────────────────

    @Test
    fun `generates get… extension for local int flag`() {
        val entries = listOf(localEntry("max_retries", "Int"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "fun ConfigValues.getMaxRetries(): Int")
        assertContains(local, "getValueCached(GeneratedLocalFlagsFeatureCheckout.maxRetries).value")
    }

    @Test
    fun `generates get… extension for local string flag`() {
        val entries = listOf(localEntry("api_url", "String"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "fun ConfigValues.getApiUrl(): String")
    }

    // ── local enum flag ───────────────────────────────────────────────────────

    @Test
    fun `generates get… extension for local enum flag`() {
        val entries = listOf(localEntry("checkout_variant", "com.example.CheckoutVariant"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "fun ConfigValues.getCheckoutVariant(): com.example.CheckoutVariant")
        assertContains(local, "getValueCached(GeneratedLocalFlagsFeatureCheckout.checkoutVariant).value")
    }

    @Test
    fun `enum extension uses get… prefix, not is…Enabled`() {
        val entries = listOf(localEntry("checkout_variant", "com.example.CheckoutVariant"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertFalse(local.contains("isCheckoutVariantEnabled"), "Enum flag must not use is…Enabled naming")
    }

    // ── remote flag ───────────────────────────────────────────────────────────

    @Test
    fun `generates get… extension returning ConfigValue for remote flag`() {
        val entries = listOf(remoteEntry("promo_banner", "Boolean"))
        val (_, remote) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(remote, "fun ConfigValues.getPromoBanner(): ConfigValue<Boolean>")
        assertContains(remote, "getValueCached(GeneratedRemoteFlagsFeatureCheckout.promoBanner)")
        assertFalse(remote.contains("suspend "), "Must not emit suspend modifier anywhere")
    }

    @Test
    fun `remote extension does not unwrap value`() {
        val entries = listOf(remoteEntry("promo_banner", "Boolean"))
        val (_, remote) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertFalse(
            remote.contains("GeneratedRemoteFlagsFeatureCheckout.promoBanner).value"),
            "Remote extensions must return full ConfigValue, not unwrapped value",
        )
    }

    // ── file structure ────────────────────────────────────────────────────────

    @Test
    fun `generated file does not contain JvmName annotation`() {
        // @file:JvmName is not supported on Kotlin/Native; class-name uniqueness is
        // achieved via the module-derived file name instead.
        val entries = listOf(localEntry("flag", "Boolean"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertFalse(local.contains("@file:JvmName"), "Generated file must not contain @file:JvmName")
    }

    @Test
    fun `generated files have package declaration`() {
        val entries = listOf(localEntry("flag", "Boolean"), remoteEntry("promo", "Boolean"))
        val (local, remote) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "package dev.androidbroadcast.featured.generated")
        assertContains(remote, "package dev.androidbroadcast.featured.generated")
    }

    @Test
    fun `generated file imports ConfigValues`() {
        val entries = listOf(localEntry("flag", "Boolean"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "import dev.androidbroadcast.featured.ConfigValues")
    }

    @Test
    fun `only the remote file imports ConfigValue`() {
        val entries = listOf(localEntry("flag", "Boolean"), remoteEntry("remote", "Boolean"))
        val (local, remote) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertFalse(local.contains("import dev.androidbroadcast.featured.ConfigValue\n"))
        assertContains(remote, "import dev.androidbroadcast.featured.ConfigValue")
    }

    @Test
    fun `generated file has auto-generated comment`() {
        val entries = listOf(localEntry("flag", "Boolean"))
        val (local, _) = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(local, "Auto-generated by Featured Gradle Plugin")
    }

    // ── generation settings ───────────────────────────────────────────────────

    @Test
    fun `custom package is used for package declaration and object import`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val (local, _) =
            ExtensionFunctionGenerator.generate(
                entries,
                modulePath,
                local = ObjectCodegen(packageName = "com.example.flags"),
            )
        assertContains(local, "package com.example.flags")
        assertContains(local, "import com.example.flags.GeneratedLocalFlagsFeatureCheckout")
    }

    @Test
    fun `custom object name is referenced in import and body`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val (local, _) =
            ExtensionFunctionGenerator.generate(
                entries,
                modulePath,
                local = ObjectCodegen(objectName = "CheckoutFlags"),
            )
        assertContains(local, "import dev.androidbroadcast.featured.generated.CheckoutFlags")
        assertContains(local, "getValueCached(CheckoutFlags.darkMode).value")
        assertFalse(local.contains("GeneratedLocalFlagsFeatureCheckout"), "Default object name must not appear")
    }

    @Test
    fun `public visibility emits public extension functions`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"), remoteEntry("promo", "Boolean"))
        val (local, remote) =
            ExtensionFunctionGenerator.generate(
                entries,
                modulePath,
                local = ObjectCodegen(visibility = FeaturedVisibility.PUBLIC),
                remote = ObjectCodegen(visibility = FeaturedVisibility.PUBLIC),
            )
        assertContains(local, "public fun ConfigValues.isDarkModeEnabled(): Boolean")
        assertContains(remote, "public fun ConfigValues.getPromo(): ConfigValue<Boolean>")
    }

    @Test
    fun `local and remote sections apply independent settings`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"), remoteEntry("promo", "Boolean"))
        val (local, remote) =
            ExtensionFunctionGenerator.generate(
                entries,
                modulePath,
                local = ObjectCodegen(packageName = "com.example.local", visibility = FeaturedVisibility.PUBLIC),
                remote = ObjectCodegen(packageName = "com.example.remote"),
            )
        assertContains(local, "package com.example.local")
        assertContains(local, "public fun ConfigValues.isDarkModeEnabled")
        assertContains(remote, "package com.example.remote")
        assertContains(remote, "internal fun ConfigValues.getPromo")
    }

    @Test
    fun `custom object name does not change the extensions file name`() {
        // The ProGuard -assumevalues class name is derived from the file name + package only,
        // so the file name must stay stable regardless of className overrides.
        assertEquals("GeneratedLocalFlagExtensionsFeatureCheckout.kt", ExtensionFunctionGenerator.localFileName(modulePath))
        assertEquals("GeneratedRemoteFlagExtensionsFeatureCheckout.kt", ExtensionFunctionGenerator.remoteFileName(modulePath))
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun localEntry(
        key: String,
        type: String,
    ) = LocalFlagEntry(
        key = key,
        defaultValue = "false",
        type = type,
        moduleName = modulePath,
        propertyName = key.toCamelCase(),
        flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
    )

    private fun remoteEntry(
        key: String,
        type: String,
    ) = LocalFlagEntry(
        key = key,
        defaultValue = "false",
        type = type,
        moduleName = modulePath,
        propertyName = key.toCamelCase(),
        flagType = LocalFlagEntry.FLAG_TYPE_REMOTE,
    )
}
