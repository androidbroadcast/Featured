package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFlagEntryKotlinReferenceTest {
    @Test
    fun `kotlinReference returns qualified reference when ownerName is set`() {
        val entry =
            LocalFlagEntry(
                key = "new_checkout",
                defaultValue = "false",
                type = "Boolean",
                moduleName = ":checkout",
                propertyName = "newCheckout",
                ownerName = "NewCheckoutFlags",
            )
        assertEquals("NewCheckoutFlags.newCheckout", entry.kotlinReference)
    }

    @Test
    fun `kotlinReference returns unqualified name for top-level property`() {
        val entry =
            LocalFlagEntry(
                key = "dark_mode",
                defaultValue = "true",
                type = "Boolean",
                moduleName = ":app",
                propertyName = "darkMode",
                ownerName = null,
            )
        assertEquals("darkMode", entry.kotlinReference)
    }

    @Test
    fun `kotlinReference returns empty string when propertyName is blank`() {
        val entry =
            LocalFlagEntry(
                key = "legacy",
                defaultValue = "false",
                type = "Boolean",
                moduleName = ":app",
                propertyName = "",
                ownerName = "Flags",
            )
        assertEquals("", entry.kotlinReference)
    }

    @Test
    fun `kotlinReference returns empty string for default-constructed entry`() {
        val entry =
            LocalFlagEntry(
                key = "k",
                defaultValue = "v",
                type = "String",
                moduleName = ":mod",
            )
        assertEquals("", entry.kotlinReference)
    }
}
