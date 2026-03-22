package dev.androidbroadcast.featured.testing

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val BoolFlag = ConfigParam("bool_flag", false)
private val StringFlag = ConfigParam("string_flag", "default")
private val IntFlag = ConfigParam("int_flag", 0)

class FakeConfigValuesTest {
    @Test
    fun `fakeConfigValues returns default value when param not set`() =
        runTest {
            val configValues = fakeConfigValues()
            val result = configValues.getValue(BoolFlag)
            assertEquals(false, result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun `fakeConfigValues returns overridden value for set param`() =
        runTest {
            val configValues =
                fakeConfigValues {
                    set(BoolFlag, true)
                }
            val result = configValues.getValue(BoolFlag)
            assertEquals(true, result.value)
        }

    @Test
    fun `fakeConfigValues supports multiple param types`() =
        runTest {
            val configValues =
                fakeConfigValues {
                    set(BoolFlag, true)
                    set(StringFlag, "overridden")
                    set(IntFlag, 42)
                }
            assertEquals(true, configValues.getValue(BoolFlag).value)
            assertEquals("overridden", configValues.getValue(StringFlag).value)
            assertEquals(42, configValues.getValue(IntFlag).value)
        }

    @Test
    fun `fakeConfigValues supports updating values mid-test`() =
        runTest {
            val configValues =
                fakeConfigValues {
                    set(BoolFlag, false)
                }
            assertEquals(false, configValues.getValue(BoolFlag).value)

            configValues.override(BoolFlag, true)
            assertEquals(true, configValues.getValue(BoolFlag).value)
        }

    @Test
    fun `fakeConfigValues observe emits initial value`() =
        runTest {
            val configValues =
                fakeConfigValues {
                    set(StringFlag, "hello")
                }
            configValues.observe(StringFlag).test {
                assertEquals("hello", awaitItem().value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fakeConfigValues observe emits updated value mid-test`() =
        runTest {
            val configValues =
                fakeConfigValues {
                    set(StringFlag, "initial")
                }
            configValues.observe(StringFlag).test {
                assertEquals("initial", awaitItem().value)
                configValues.override(StringFlag, "updated")
                assertEquals("updated", awaitItem().value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fakeConfigValues observe falls back to default when not set`() =
        runTest {
            val configValues = fakeConfigValues()
            configValues.observe(IntFlag).test {
                assertEquals(0, awaitItem().value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigValues fake companion factory is equivalent to fakeConfigValues builder`() =
        runTest {
            val configValues =
                ConfigValues.fake {
                    set(BoolFlag, true)
                    set(StringFlag, "test")
                }
            assertEquals(true, configValues.getValue(BoolFlag).value)
            assertEquals("test", configValues.getValue(StringFlag).value)
        }

    @Test
    fun `ConfigValues fake companion factory returns default for unset param`() =
        runTest {
            val configValues = ConfigValues.fake { set(BoolFlag, true) }
            assertEquals(0, configValues.getValue(IntFlag).value)
        }
}
