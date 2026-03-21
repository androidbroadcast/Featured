package dev.androidbroadcast.featured.compose

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.observeValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalConfigValuesTest {
    private val stringParam = ConfigParam("test_string", "default_string")
    private val intParam = ConfigParam("test_int", 42)
    private val boolParam = ConfigParam("test_bool", true)

    // --- default (no-op) fake used as LocalConfigValues.current in previews ---

    @Test
    fun defaultFakeConfigValuesReturnsDefaultValueForStringParam() = runTest {
        val configValues = fakeConfigValues()

        val configValue = configValues.getValue(stringParam)

        assertEquals("default_string", configValue.value)
    }

    @Test
    fun defaultFakeConfigValuesReturnsDefaultValueForIntParam() = runTest {
        val configValues = fakeConfigValues()

        val configValue = configValues.getValue(intParam)

        assertEquals(42, configValue.value)
    }

    @Test
    fun defaultFakeConfigValuesObserveEmitsDefaultValue() = runTest {
        val configValues = fakeConfigValues()

        val value = configValues.observeValue(stringParam).first()

        assertEquals("default_string", value)
    }

    // --- fakeConfigValues builder with overrides ---

    @Test
    fun fakeConfigValuesWithNoOverridesReturnsDefaultValue() = runTest {
        val fake = fakeConfigValues { }

        val configValue = fake.getValue(stringParam)

        assertEquals("default_string", configValue.value)
    }

    @Test
    fun fakeConfigValuesWithOverrideReturnsOverriddenValue() = runTest {
        val fake = fakeConfigValues {
            set(stringParam, "overridden_value")
        }

        val configValue = fake.getValue(stringParam)

        assertEquals("overridden_value", configValue.value)
    }

    @Test
    fun fakeConfigValuesWithMultipleOverridesReturnCorrectValues() = runTest {
        val fake = fakeConfigValues {
            set(stringParam, "custom_string")
            set(intParam, 99)
            set(boolParam, false)
        }

        assertEquals("custom_string", fake.getValue(stringParam).value)
        assertEquals(99, fake.getValue(intParam).value)
        assertEquals(false, fake.getValue(boolParam).value)
    }

    @Test
    fun fakeConfigValuesObserveEmitsOverriddenValue() = runTest {
        val fake = fakeConfigValues {
            set(stringParam, "observed_value")
        }

        val value = fake.observeValue(stringParam).first()

        assertEquals("observed_value", value)
    }

    @Test
    fun fakeConfigValuesUnsetParamReturnsDefaultValue() = runTest {
        val fake = fakeConfigValues {
            set(intParam, 7)
        }

        val configValue = fake.getValue(stringParam)

        assertEquals("default_string", configValue.value)
    }
}
