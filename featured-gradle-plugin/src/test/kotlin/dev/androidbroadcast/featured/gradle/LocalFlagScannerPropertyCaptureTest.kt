package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalFlagScannerPropertyCaptureTest {
    @Test
    fun `scanner captures property name for top-level flag`() {
        val source =
            """
            package com.example
            @LocalFlag
            val darkMode = ConfigParam("dark_mode", false)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":app")

        assertEquals(1, result.size)
        assertEquals("darkMode", result[0].propertyName)
    }

    @Test
    fun `scanner captures null owner for top-level flag`() {
        val source =
            """
            package com.example
            @LocalFlag
            val darkMode = ConfigParam("dark_mode", false)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":app")

        assertEquals(1, result.size)
        assertNull(result[0].ownerName, "Top-level property should have null ownerName")
    }

    @Test
    fun `scanner captures owner name for flag inside object`() {
        val source =
            """
            package com.example
            object NewCheckoutFlags {
                @LocalFlag
                val newCheckout = ConfigParam("new_checkout", false)
            }
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":checkout")

        assertEquals(1, result.size)
        assertEquals("newCheckout", result[0].propertyName)
        assertEquals("NewCheckoutFlags", result[0].ownerName)
    }

    @Test
    fun `scanner captures property name with named args`() {
        val source =
            """
            package com.example
            @LocalFlag
            val featureTimeout = ConfigParam<Int>(key = "timeout", defaultValue = 30)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":net")

        assertEquals(1, result.size)
        assertEquals("featureTimeout", result[0].propertyName)
    }

    @Test
    fun `scanner captures correct kotlin reference for object property`() {
        val source =
            """
            package com.example
            object AppFlags {
                @LocalFlag
                val darkTheme = ConfigParam("dark_theme", true)
            }
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":app")

        assertEquals(1, result.size)
        assertEquals("AppFlags.darkTheme", result[0].kotlinReference)
    }

    @Test
    fun `scanner captures multiple flags inside same object with correct owner`() {
        val source =
            """
            package com.example
            object FeatureFlags {
                @LocalFlag
                val flagA = ConfigParam("flag_a", false)
                @LocalFlag
                val flagB = ConfigParam("flag_b", true)
            }
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":app")

        assertEquals(2, result.size)
        assertEquals("FeatureFlags", result[0].ownerName)
        assertEquals("flagA", result[0].propertyName)
        assertEquals("FeatureFlags", result[1].ownerName)
        assertEquals("flagB", result[1].propertyName)
    }

    @Test
    fun `scanner preserves existing key and type extraction alongside new fields`() {
        val source =
            """
            package com.example
            object Flags {
                @LocalFlag
                val retryCount = ConfigParam<Int>(key = "retry_count", defaultValue = 5)
            }
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = ":core")

        assertEquals(1, result.size)
        val entry = result[0]
        assertEquals("retry_count", entry.key)
        assertEquals("5", entry.defaultValue)
        assertEquals("Int", entry.type)
        assertEquals(":core", entry.moduleName)
        assertEquals("retryCount", entry.propertyName)
        assertEquals("Flags", entry.ownerName)
    }
}
