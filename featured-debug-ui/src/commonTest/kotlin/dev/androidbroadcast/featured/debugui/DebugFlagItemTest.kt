package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebugFlagItemTest {
    @Test
    fun itemDisplaysKeyAsName() {
        val param = ConfigParam(key = "my_flag", defaultValue = true)
        val item = DebugFlagItem(param = param, currentValue = true, overrideValue = null)
        assertEquals("my_flag", item.key)
    }

    @Test
    fun itemExposesDefaultValue() {
        val param = ConfigParam(key = "flag_x", defaultValue = 42)
        val item = DebugFlagItem(param = param, currentValue = 42, overrideValue = null)
        assertEquals(42, item.defaultValue)
    }

    @Test
    fun itemReportsOverrideActiveWhenOverrideValueSet() {
        val param = ConfigParam(key = "flag_y", defaultValue = false)
        val item = DebugFlagItem(param = param, currentValue = true, overrideValue = true)
        assertTrue(item.isOverridden)
    }

    @Test
    fun itemReportsOverrideInactiveWhenNoOverride() {
        val param = ConfigParam(key = "flag_z", defaultValue = false)
        val item = DebugFlagItem(param = param, currentValue = false, overrideValue = null)
        assertFalse(item.isOverridden)
    }

    @Test
    fun itemExposesNullOverrideWhenNotOverridden() {
        val param = ConfigParam(key = "flag_w", defaultValue = "hello")
        val item = DebugFlagItem(param = param, currentValue = "hello", overrideValue = null)
        assertNull(item.overrideValue)
    }

    @Test
    fun itemExposesCategory() {
        val param = ConfigParam(key = "flag_cat", defaultValue = true, category = "UI")
        val item = DebugFlagItem(param = param, currentValue = true, overrideValue = null)
        assertEquals("UI", item.category)
    }

    @Test
    fun itemExposesCategoryNullWhenNotSet() {
        val param = ConfigParam(key = "flag_nocat", defaultValue = true)
        val item = DebugFlagItem(param = param, currentValue = true, overrideValue = null)
        assertNull(item.category)
    }

    @Test
    fun itemExposesDescription() {
        val param = ConfigParam(key = "flag_desc", defaultValue = true, description = "Enables feature X")
        val item = DebugFlagItem(param = param, currentValue = true, overrideValue = null)
        assertEquals("Enables feature X", item.description)
    }
}
