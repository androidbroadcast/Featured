package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFlagEntryKotlinReferenceTest {
    @Test
    fun `kotlinReference for local flag uses GeneratedLocalFlags object`() {
        val entry = LocalFlagEntry(
            key = "dark_mode",
            defaultValue = "false",
            type = "Boolean",
            moduleName = ":app",
            propertyName = "darkMode",
            flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
        )
        assertEquals("${LocalFlagEntry.GENERATED_LOCAL_OBJECT}.darkMode", entry.kotlinReference)
    }

    @Test
    fun `kotlinReference for remote flag uses GeneratedRemoteFlags object`() {
        val entry = LocalFlagEntry(
            key = "promo_banner",
            defaultValue = "false",
            type = "Boolean",
            moduleName = ":app",
            propertyName = "promoBanner",
            flagType = LocalFlagEntry.FLAG_TYPE_REMOTE,
        )
        assertEquals("${LocalFlagEntry.GENERATED_REMOTE_OBJECT}.promoBanner", entry.kotlinReference)
    }

    @Test
    fun `kotlinReference returns empty string when propertyName is blank`() {
        val entry = LocalFlagEntry(
            key = "legacy",
            defaultValue = "false",
            type = "Boolean",
            moduleName = ":app",
            propertyName = "",
            flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
        )
        assertEquals("", entry.kotlinReference)
    }

    @Test
    fun `kotlinReference returns empty string for default-constructed entry`() {
        val entry = LocalFlagEntry(key = "k", defaultValue = "v", type = "String", moduleName = ":mod")
        assertEquals("", entry.kotlinReference)
    }

    @Test
    fun `isLocal is true for local flagType`() {
        val entry = LocalFlagEntry(
            key = "k", defaultValue = "v", type = "String", moduleName = ":mod",
            flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
        )
        assertEquals(true, entry.isLocal)
    }

    @Test
    fun `isLocal is false for remote flagType`() {
        val entry = LocalFlagEntry(
            key = "k", defaultValue = "v", type = "String", moduleName = ":mod",
            flagType = LocalFlagEntry.FLAG_TYPE_REMOTE,
        )
        assertEquals(false, entry.isLocal)
    }
}
