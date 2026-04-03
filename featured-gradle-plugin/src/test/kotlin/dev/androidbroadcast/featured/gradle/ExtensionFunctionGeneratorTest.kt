package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionFunctionGeneratorTest {
    private val modulePath = ":feature:checkout"

    // ── JVM file name ─────────────────────────────────────────────────────────

    @Test
    fun `jvmFileName for app module`() {
        val name = ExtensionFunctionGenerator.jvmFileName(":app")
        assertContains(name, "App")
        assertContains(name, "FlagExtensionsKt")
    }

    @Test
    fun `jvmFileName for nested module`() {
        val name = ExtensionFunctionGenerator.jvmFileName(":feature:checkout")
        assertContains(name, "FeatureCheckout")
    }

    @Test
    fun `jvmFileName for different modules are distinct`() {
        val a = ExtensionFunctionGenerator.jvmFileName(":feature:checkout")
        val b = ExtensionFunctionGenerator.jvmFileName(":feature:ui")
        assertFalse(a == b, "Different modules must produce distinct JVM names")
    }

    // ── empty input ───────────────────────────────────────────────────────────

    @Test
    fun `returns empty string for empty entries`() {
        val source = ExtensionFunctionGenerator.generate(emptyList(), modulePath)
        assertTrue(source.isEmpty())
    }

    // ── local boolean flag ────────────────────────────────────────────────────

    @Test
    fun `generates is…Enabled extension for local boolean flag`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "fun ConfigValues.isDarkModeEnabled(): Boolean")
    }

    @Test
    fun `local boolean extension returns raw value`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "getValue(GeneratedLocalFlags.darkMode).value")
    }

    @Test
    fun `local boolean extension is public`() {
        val entries = listOf(localEntry("dark_mode", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "public fun ConfigValues.isDarkModeEnabled()")
    }

    // ── local non-boolean flag ────────────────────────────────────────────────

    @Test
    fun `generates get… extension for local int flag`() {
        val entries = listOf(localEntry("max_retries", "Int"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "fun ConfigValues.getMaxRetries(): Int")
        assertContains(source, "getValue(GeneratedLocalFlags.maxRetries).value")
    }

    @Test
    fun `generates get… extension for local string flag`() {
        val entries = listOf(localEntry("api_url", "String"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "fun ConfigValues.getApiUrl(): String")
    }

    // ── remote flag ───────────────────────────────────────────────────────────

    @Test
    fun `generates get… extension returning ConfigValue for remote flag`() {
        val entries = listOf(remoteEntry("promo_banner", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "fun ConfigValues.getPromoBanner(): ConfigValue<Boolean>")
        assertContains(source, "getValue(GeneratedRemoteFlags.promoBanner)")
    }

    @Test
    fun `remote extension does not unwrap value`() {
        val entries = listOf(remoteEntry("promo_banner", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertFalse(
            source.contains("GeneratedRemoteFlags.promoBanner).value"),
            "Remote extensions must return full ConfigValue, not unwrapped value",
        )
    }

    // ── file structure ────────────────────────────────────────────────────────

    @Test
    fun `generated file has JvmName annotation`() {
        val entries = listOf(localEntry("flag", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "@file:JvmName(\"${ExtensionFunctionGenerator.jvmFileName(modulePath)}\")")
    }

    @Test
    fun `generated file has package declaration`() {
        val entries = listOf(localEntry("flag", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "package dev.androidbroadcast.featured.generated")
    }

    @Test
    fun `generated file imports ConfigValues`() {
        val entries = listOf(localEntry("flag", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "import dev.androidbroadcast.featured.ConfigValues")
    }

    @Test
    fun `imports ConfigValue only when remote flags are present`() {
        val localOnly = listOf(localEntry("flag", "Boolean"))
        val withRemote = listOf(localEntry("flag", "Boolean"), remoteEntry("remote", "Boolean"))
        assertFalse(
            ExtensionFunctionGenerator.generate(localOnly, modulePath).contains("import dev.androidbroadcast.featured.ConfigValue\n"),
        )
        assertContains(ExtensionFunctionGenerator.generate(withRemote, modulePath), "import dev.androidbroadcast.featured.ConfigValue")
    }

    @Test
    fun `generated file has auto-generated comment`() {
        val entries = listOf(localEntry("flag", "Boolean"))
        val source = ExtensionFunctionGenerator.generate(entries, modulePath)
        assertContains(source, "Auto-generated by Featured Gradle Plugin")
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
