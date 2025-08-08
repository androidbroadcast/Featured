package dev.androidbroadcast.featured

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigValueTest {

    @Test
    fun testConfigValueCreation() {
        val value = ConfigValue("test", ConfigValue.Source.LOCAL)

        assertEquals("test", value.value)
        assertEquals(ConfigValue.Source.LOCAL, value.source)
    }

    @Test
    fun testConfigValueSources() {
        val defaultValue = ConfigValue(42, ConfigValue.Source.DEFAULT)
        assertEquals(ConfigValue.Source.DEFAULT, defaultValue.source)

        val remoteValue = ConfigValue(42, ConfigValue.Source.REMOTE)
        assertEquals(ConfigValue.Source.REMOTE, remoteValue.source)

        val remoteDefaultValue = ConfigValue(42, ConfigValue.Source.REMOTE_DEFAULT)
        assertEquals(ConfigValue.Source.REMOTE_DEFAULT, remoteDefaultValue.source)

        val localValue = ConfigValue(42, ConfigValue.Source.LOCAL)
        assertEquals(ConfigValue.Source.LOCAL, localValue.source)

        val unknownValue = ConfigValue(42, ConfigValue.Source.UNKNOWN)
        assertEquals(ConfigValue.Source.UNKNOWN, unknownValue.source)
    }

    @Test
    fun testConfigValueMap() {
        val stringValue = ConfigValue("123", ConfigValue.Source.LOCAL)
        val intValue = stringValue.map { it.toInt() }

        assertEquals(123, intValue.value)
        assertEquals(ConfigValue.Source.LOCAL, intValue.source)
    }

    @Test
    fun testConfigValueMapTransformation() {
        val boolValue = ConfigValue(true, ConfigValue.Source.REMOTE)
        val stringValue = boolValue.map { if (it) "enabled" else "disabled" }

        assertEquals("enabled", stringValue.value)
        assertEquals(ConfigValue.Source.REMOTE, stringValue.source)
    }

    @Test
    fun testConfigValueDoIf() {
        val value = ConfigValue(10, ConfigValue.Source.LOCAL)
        var actionCalled = false
        var elseActionCalled = false

        value.doIf(
            predicate = { it.value > 5 },
            action = { actionCalled = true },
            elseAction = { elseActionCalled = true }
        )

        assertEquals(true, actionCalled)
        assertEquals(false, elseActionCalled)
    }

    @Test
    fun testConfigValueDoIfElse() {
        val value = ConfigValue(3, ConfigValue.Source.REMOTE)
        var actionCalled = false
        var elseActionCalled = false

        value.doIf(
            predicate = { it.value > 5 },
            action = { actionCalled = true },
            elseAction = { elseActionCalled = true }
        )

        assertEquals(false, actionCalled)
        assertEquals(true, elseActionCalled)
    }

    @Test
    fun testConfigValueDoIfWithoutElseAction() {
        val value = ConfigValue(10, ConfigValue.Source.DEFAULT)
        var actionCalled = false

        value.doIf(
            predicate = { it.value > 5 },
            action = { actionCalled = true }
        )

        assertEquals(true, actionCalled)
    }
}
