package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugFlagItemSourceTest {
    @Test
    fun itemExposesSourceDefault() {
        val param = ConfigParam(key = "flag_src", defaultValue = true)
        val item =
            DebugFlagItem(
                param = param,
                currentValue = true,
                overrideValue = null,
                source = ConfigValue.Source.DEFAULT,
            )
        assertEquals(ConfigValue.Source.DEFAULT, item.source)
    }

    @Test
    fun itemExposesSourceLocal() {
        val param = ConfigParam(key = "flag_local", defaultValue = false)
        val item =
            DebugFlagItem(
                param = param,
                currentValue = true,
                overrideValue = true,
                source = ConfigValue.Source.LOCAL,
            )
        assertEquals(ConfigValue.Source.LOCAL, item.source)
    }

    @Test
    fun itemExposesSourceRemote() {
        val param = ConfigParam(key = "flag_remote", defaultValue = false)
        val item =
            DebugFlagItem(
                param = param,
                currentValue = true,
                overrideValue = null,
                source = ConfigValue.Source.REMOTE,
            )
        assertEquals(ConfigValue.Source.REMOTE, item.source)
    }

    @Test
    fun itemIsOverriddenWhenSourceIsLocal() {
        val param = ConfigParam(key = "flag_override", defaultValue = false)
        val item =
            DebugFlagItem(
                param = param,
                currentValue = true,
                overrideValue = true,
                source = ConfigValue.Source.LOCAL,
            )
        assertTrue(item.isOverridden)
    }

    @Test
    fun itemIsNotOverriddenWhenSourceIsDefault() {
        val param = ConfigParam(key = "flag_not_override", defaultValue = true)
        val item =
            DebugFlagItem(
                param = param,
                currentValue = true,
                overrideValue = null,
                source = ConfigValue.Source.DEFAULT,
            )
        assertFalse(item.isOverridden)
    }
}
