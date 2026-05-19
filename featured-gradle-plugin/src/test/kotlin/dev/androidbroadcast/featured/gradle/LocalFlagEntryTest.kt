package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFlagEntryTest {
    @Test
    fun `isLocal is true for local flagType`() {
        val entry =
            LocalFlagEntry(
                key = "k",
                defaultValue = "v",
                type = "String",
                moduleName = ":mod",
                flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
            )
        assertEquals(true, entry.isLocal)
    }

    @Test
    fun `isLocal is false for remote flagType`() {
        val entry =
            LocalFlagEntry(
                key = "k",
                defaultValue = "v",
                type = "String",
                moduleName = ":mod",
                flagType = LocalFlagEntry.FLAG_TYPE_REMOTE,
            )
        assertEquals(false, entry.isLocal)
    }
}
