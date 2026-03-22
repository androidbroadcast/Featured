package dev.androidbroadcast.featured.nsuserdefaults

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NSUserDefaultsConfigValueProviderTest {

    private val suiteName = "dev.androidbroadcast.featured.test.${kotlin.random.Random.nextLong()}"
    private lateinit var provider: NSUserDefaultsConfigValueProvider

    @BeforeTest
    fun setUp() {
        provider = NSUserDefaultsConfigValueProvider(suiteName = suiteName)
    }

    @AfterTest
    fun tearDown() {
        provider.removeSuite()
    }

    // --- get ---

    @Test
    fun `get returns null when key not set`() = runTest {
        val param = ConfigParam("missing_key", false)
        assertNull(provider.get(param))
    }

    @Test
    fun `get returns stored Boolean value`() = runTest {
        val param = ConfigParam("bool_key", false)
        provider.set(param, true)
        val result = provider.get(param)
        assertEquals(ConfigValue(true, ConfigValue.Source.LOCAL), result)
    }

    @Test
    fun `get returns stored Int value`() = runTest {
        val param = ConfigParam("int_key", 0)
        provider.set(param, 42)
        val result = provider.get(param)
        assertEquals(ConfigValue(42, ConfigValue.Source.LOCAL), result)
    }

    @Test
    fun `get returns stored Long value`() = runTest {
        val param = ConfigParam("long_key", 0L)
        provider.set(param, 123456789L)
        val result = provider.get(param)
        assertEquals(ConfigValue(123456789L, ConfigValue.Source.LOCAL), result)
    }

    @Test
    fun `get returns stored Double value`() = runTest {
        val param = ConfigParam("double_key", 0.0)
        provider.set(param, 3.14)
        val result = provider.get(param)
        assertEquals(ConfigValue(3.14, ConfigValue.Source.LOCAL), result)
    }

    @Test
    fun `get returns stored Float value`() = runTest {
        val param = ConfigParam("float_key", 0f)
        provider.set(param, 2.5f)
        val result = provider.get(param)
        assertEquals(ConfigValue(2.5f, ConfigValue.Source.LOCAL), result)
    }

    @Test
    fun `get returns stored String value`() = runTest {
        val param = ConfigParam("string_key", "")
        provider.set(param, "hello")
        val result = provider.get(param)
        assertEquals(ConfigValue("hello", ConfigValue.Source.LOCAL), result)
    }

    // --- resetOverride ---

    @Test
    fun `resetOverride makes get return null`() = runTest {
        val param = ConfigParam("reset_key", false)
        provider.set(param, true)
        provider.resetOverride(param)
        assertNull(provider.get(param))
    }

    // --- clear ---

    @Test
    fun `clear removes all values`() = runTest {
        val boolParam = ConfigParam("clear_bool", false)
        val strParam = ConfigParam("clear_str", "")
        provider.set(boolParam, true)
        provider.set(strParam, "value")
        provider.clear()
        assertNull(provider.get(boolParam))
        assertNull(provider.get(strParam))
    }

    // --- observe ---

    @Test
    fun `observe emits current value immediately when set`() = runTest {
        val param = ConfigParam("observe_key", false)
        provider.set(param, true)
        val emitted = provider.observe(param).first()
        assertEquals(ConfigValue(true, ConfigValue.Source.LOCAL), emitted)
    }

    @Test
    fun `observe emits updated value after set`() = runTest {
        val param = ConfigParam("observe_update_key", 0)
        provider.set(param, 1)

        provider.observe(param).test {
            // First emission: current stored value
            assertEquals(ConfigValue(1, ConfigValue.Source.LOCAL), awaitItem())

            // Reactive emission: new value after set
            provider.set(param, 99)
            assertEquals(ConfigValue(99, ConfigValue.Source.LOCAL), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
