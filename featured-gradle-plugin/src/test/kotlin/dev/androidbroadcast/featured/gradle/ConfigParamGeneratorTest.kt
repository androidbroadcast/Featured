package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigParamGeneratorTest {
    // ── local flags ───────────────────────────────────────────────────────────

    @Test
    fun `generates GeneratedLocalFlags object for local boolean flag`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "object GeneratedLocalFlags")
        assertContains(local, "val darkMode = ConfigParam<Boolean>")
    }

    @Test
    fun `generated local ConfigParam uses named key argument`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "key = \"dark_mode\"")
        assertContains(local, "defaultValue = false")
    }

    @Test
    fun `generated local ConfigParam includes description when present`() {
        val entry = localEntry("dark_mode", "false", "Boolean").copy(description = "Enable dark mode")
        val (local, _) = ConfigParamGenerator.generate(listOf(entry))
        assertContains(local, "description = \"Enable dark mode\"")
    }

    @Test
    fun `generated local ConfigParam includes category when present`() {
        val entry = localEntry("dark_mode", "false", "Boolean").copy(category = "UI")
        val (local, _) = ConfigParamGenerator.generate(listOf(entry))
        assertContains(local, "category = \"UI\"")
    }

    @Test
    fun `generated local ConfigParam omits null description`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertTrue(!local.contains("description ="), "Null description must not appear in output")
    }

    @Test
    fun `local object is internal`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "internal object GeneratedLocalFlags")
    }

    @Test
    fun `formats Long default with L suffix`() {
        val entries = listOf(localEntry("timeout", "5000L", "Long"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "defaultValue = 5000L")
    }

    @Test
    fun `formats Float default with f suffix`() {
        val entries = listOf(localEntry("ratio", "0.5f", "Float"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "defaultValue = 0.5f")
    }

    @Test
    fun `passes String default as-is (already quoted)`() {
        val entries = listOf(localEntry("url", "\"https://x.com\"", "String"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "defaultValue = \"https://x.com\"")
    }

    // ── remote flags ──────────────────────────────────────────────────────────

    @Test
    fun `generates GeneratedRemoteFlags object for remote flag`() {
        val entries = listOf(remoteEntry("promo_banner", "false", "Boolean"))
        val (_, remote) = ConfigParamGenerator.generate(entries)
        assertContains(remote, "object GeneratedRemoteFlags")
        assertContains(remote, "val promoBanner = ConfigParam<Boolean>")
    }

    @Test
    fun `remote object is internal`() {
        val entries = listOf(remoteEntry("promo", "false", "Boolean"))
        val (_, remote) = ConfigParamGenerator.generate(entries)
        assertContains(remote, "internal object GeneratedRemoteFlags")
    }

    // ── empty cases ───────────────────────────────────────────────────────────

    @Test
    fun `returns empty string for local when no local flags`() {
        val entries = listOf(remoteEntry("promo", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertTrue(local.isEmpty(), "Expected empty local source when no local flags")
    }

    @Test
    fun `returns empty string for remote when no remote flags`() {
        val entries = listOf(localEntry("dark_mode", "false", "Boolean"))
        val (_, remote) = ConfigParamGenerator.generate(entries)
        assertTrue(remote.isEmpty(), "Expected empty remote source when no remote flags")
    }

    @Test
    fun `both empty for empty entries list`() {
        val (local, remote) = ConfigParamGenerator.generate(emptyList())
        assertEquals("", local)
        assertEquals("", remote)
    }

    // ── imports ───────────────────────────────────────────────────────────────

    @Test
    fun `generated local file imports ConfigParam`() {
        val entries = listOf(localEntry("flag", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "import dev.androidbroadcast.featured.ConfigParam")
    }

    @Test
    fun `generated file has auto-generated comment`() {
        val entries = listOf(localEntry("flag", "false", "Boolean"))
        val (local, _) = ConfigParamGenerator.generate(entries)
        assertContains(local, "Auto-generated by Featured Gradle Plugin")
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
        moduleName = ":app",
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
        moduleName = ":app",
        propertyName = key.toCamelCase(),
        flagType = LocalFlagEntry.FLAG_TYPE_REMOTE,
    )
}
