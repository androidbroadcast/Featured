package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlagContainerTest {
    @Test
    fun `boolean flag has correct type and default`() {
        val container = FlagContainer().apply { boolean("dark_mode", default = false) }
        val flag = container.flags.single()
        assertEquals("dark_mode", flag.key)
        assertEquals("false", flag.defaultValue)
        assertEquals("Boolean", flag.type)
    }

    @Test
    fun `boolean true default serialised correctly`() {
        val container = FlagContainer().apply { boolean("btn_red", default = true) }
        assertEquals("true", container.flags.single().defaultValue)
    }

    @Test
    fun `int flag has correct type`() {
        val container = FlagContainer().apply { int("max_retries", default = 3) }
        val flag = container.flags.single()
        assertEquals("Int", flag.type)
        assertEquals("3", flag.defaultValue)
    }

    @Test
    fun `long flag has correct type`() {
        val container = FlagContainer().apply { long("timeout", default = 5000L) }
        assertEquals("Long", container.flags.single().type)
        assertEquals("5000", container.flags.single().defaultValue)
    }

    @Test
    fun `float flag has correct type`() {
        val container = FlagContainer().apply { float("ratio", default = 0.5f) }
        assertEquals("Float", container.flags.single().type)
    }

    @Test
    fun `double flag has correct type`() {
        val container = FlagContainer().apply { double("threshold", default = 1.5) }
        assertEquals("Double", container.flags.single().type)
    }

    @Test
    fun `string flag value is quoted in descriptor`() {
        val container = FlagContainer().apply { string("url", default = "https://x.com") }
        val flag = container.flags.single()
        assertEquals("String", flag.type)
        assertEquals("\"https://x.com\"", flag.defaultValue)
    }

    @Test
    fun `configure block sets description and category`() {
        val container =
            FlagContainer().apply {
                boolean("dark_mode", default = false) {
                    description = "Enable dark mode"
                    category = "UI"
                    expiresAt = "2026-12-01"
                }
            }
        val flag = container.flags.single()
        assertEquals("Enable dark mode", flag.description)
        assertEquals("UI", flag.category)
        assertEquals("2026-12-01", flag.expiresAt)
    }

    @Test
    fun `multiple flags are all stored`() {
        val container =
            FlagContainer().apply {
                boolean("flag_a", default = false)
                int("flag_b", default = 1)
                string("flag_c", default = "x")
            }
        assertEquals(3, container.flags.size)
    }

    @Test
    fun `toDescriptors serialises all flags`() {
        val container =
            FlagContainer().apply {
                boolean("dark_mode", default = false) { category = "UI" }
            }
        val descriptors = container.toDescriptors()
        assertEquals(1, descriptors.size)
        assertContains(descriptors.first(), "dark_mode")
        assertContains(descriptors.first(), "false")
        assertContains(descriptors.first(), "Boolean")
        assertContains(descriptors.first(), "UI")
    }

    @Test
    fun `empty container has no flags`() {
        assertTrue(FlagContainer().flags.isEmpty())
    }
}
