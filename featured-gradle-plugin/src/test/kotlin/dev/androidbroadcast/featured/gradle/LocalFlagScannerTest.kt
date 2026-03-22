package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalFlagScannerTest {
    @Test
    fun `scanner returns empty list when no LocalFlag annotations present`() {
        val source =
            """
            package com.example
            val myParam = ConfigParam("key", true)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanner extracts boolean flag with positional args`() {
        val source =
            """
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
    fun `scanner extracts string flag with positional args`() {
        val source =
            """
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
    fun `scanner extracts integer flag with positional args`() {
        val source =
            """
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
    fun `scanner extracts double value`() {
        val source =
            """
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
    fun `scanner handles Long literal with L suffix`() {
        val source =
            """
            package com.example
            @LocalFlag
            val bigNumber = ConfigParam("big_number", 123456789L)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "core")

        assertEquals(1, result.size)
        assertEquals("Long", result[0].type)
        assertEquals("123456789", result[0].defaultValue)
    }

    @Test
    fun `scanner handles Float literal with f suffix`() {
        val source =
            """
            package com.example
            @LocalFlag
            val ratio = ConfigParam("ratio", 3.14f)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "core")

        assertEquals(1, result.size)
        assertEquals("Float", result[0].type)
        assertEquals("3.14", result[0].defaultValue)
    }

    @Test
    fun `scanner extracts flag with named arguments`() {
        val source =
            """
            package com.example
            @LocalFlag
            val timeout = ConfigParam<Int>(key = "timeout", defaultValue = 30)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "network")

        assertEquals(1, result.size)
        assertEquals(
            LocalFlagEntry(
                key = "timeout",
                defaultValue = "30",
                type = "Int",
                moduleName = "network",
            ),
            result[0],
        )
    }

    @Test
    fun `scanner extracts flag with named boolean defaultValue`() {
        val source =
            """
            package com.example
            @LocalFlag
            val featureEnabled = ConfigParam<Boolean>(key = "feature_enabled", defaultValue = true)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertEquals(1, result.size)
        assertEquals("Boolean", result[0].type)
        assertEquals("true", result[0].defaultValue)
    }

    @Test
    fun `scanner extracts multiple flags from same source`() {
        val source =
            """
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
    fun `scanner skips unannotated ConfigParam`() {
        val source =
            """
            package com.example
            val notAFlag = ConfigParam("not_a_flag", false)
            @LocalFlag
            val isAFlag = ConfigParam("is_a_flag", true)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertEquals(1, result.size)
        assertEquals("is_a_flag", result[0].key)
    }

    @Test
    fun `scanner ignores RemoteFlag-annotated ConfigParam - not included in xcconfig or const val generation`() {
        // @RemoteFlag declarations must never appear in LocalFlagEntry results;
        // XcconfigGenerator and IosConstValGenerator only receive LocalFlagEntry lists,
        // so excluding @RemoteFlag at scan time guarantees nothing is generated for them.
        val source =
            """
            package com.example
            @RemoteFlag
            val remoteFeature = ConfigParam("remote_feature", false)
            @LocalFlag
            val localFeature = ConfigParam("local_feature", false)
            """.trimIndent()

        val result = LocalFlagScanner.scan(source, moduleName = "app")

        assertEquals(1, result.size, "Expected only @LocalFlag entry, not @RemoteFlag")
        assertEquals("local_feature", result[0].key)
    }

    @Test
    fun `scanner ignores RemoteFlag-only source - generators produce no iOS output`() {
        val source =
            """
            package com.example
            @RemoteFlag
            val newCheckoutFlow = ConfigParam("new_checkout_flow", false)
            @RemoteFlag
            val darkMode = ConfigParam("dark_mode", true)
            """.trimIndent()

        val entries = LocalFlagScanner.scan(source, moduleName = "app")

        assertTrue(entries.isEmpty(), "Expected no entries from @RemoteFlag-only source")
        assertTrue(
            XcconfigGenerator.generate(entries).isBlank(),
            "Expected no xcconfig output for @RemoteFlag flags",
        )
        assertTrue(
            IosConstValGenerator.generate(entries).isBlank(),
            "Expected no const val output for @RemoteFlag flags",
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
