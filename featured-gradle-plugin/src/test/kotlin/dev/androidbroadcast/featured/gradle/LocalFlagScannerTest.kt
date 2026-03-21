package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalFlagScannerTest {
    @Test
    fun `scanner returns empty list when no LocalFlag annotations present`() {
        val source = """
            package com.example
            val myParam = ConfigParam("key", true)
        """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanner extracts boolean flag annotated with LocalFlag`() {
        val source = """
            package com.example
            @LocalFlag
            val darkMode = ConfigParam("dark_mode", false)
        """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertEquals(1, result.size)
        assertEquals(
            LocalFlagEntry(
                key = "dark_mode",
                defaultValue = "false",
                type = "Boolean",
                moduleName = "app",
            ),
            result[0],
        )
    }

    @Test
    fun `scanner extracts string flag annotated with LocalFlag`() {
        val source = """
            package com.example
            @LocalFlag
            val serverUrl = ConfigParam("server_url", "https://example.com")
        """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "network")

        assertEquals(1, result.size)
        assertEquals(
            LocalFlagEntry(
                key = "server_url",
                defaultValue = "https://example.com",
                type = "String",
                moduleName = "network",
            ),
            result[0],
        )
    }

    @Test
    fun `scanner extracts integer flag annotated with LocalFlag`() {
        val source = """
            package com.example
            @LocalFlag
            val retryCount = ConfigParam("retry_count", 3)
        """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "core")

        assertEquals(1, result.size)
        assertEquals(
            LocalFlagEntry(
                key = "retry_count",
                defaultValue = "3",
                type = "Int",
                moduleName = "core",
            ),
            result[0],
        )
    }

    @Test
    fun `scanner extracts multiple flags from same source`() {
        val source = """
            package com.example
            @LocalFlag
            val darkMode = ConfigParam("dark_mode", false)

            val ignored = ConfigParam("ignored", true)

            @LocalFlag
            val timeout = ConfigParam("timeout", 30)
        """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertEquals(2, result.size)
        assertEquals("dark_mode", result[0].key)
        assertEquals("timeout", result[1].key)
    }

    @Test
    fun `scanner handles double value`() {
        val source = """
            package com.example
            @LocalFlag
            val threshold = ConfigParam("threshold", 0.5)
        """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "ml")

        assertEquals(1, result.size)
        assertEquals(
            LocalFlagEntry(
                key = "threshold",
                defaultValue = "0.5",
                type = "Double",
                moduleName = "ml",
            ),
            result[0],
        )
    }

    @Test
    fun `LocalFlagEntry data class equality works correctly`() {
        val entry1 = LocalFlagEntry("k", "v", "String", "mod")
        val entry2 = LocalFlagEntry("k", "v", "String", "mod")
        val entry3 = LocalFlagEntry("k2", "v", "String", "mod")

        assertEquals(entry1, entry2)
        assertTrue(entry1 != entry3)
    }
}
