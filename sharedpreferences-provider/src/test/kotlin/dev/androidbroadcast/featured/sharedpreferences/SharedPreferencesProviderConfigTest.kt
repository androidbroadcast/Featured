package dev.androidbroadcast.featured.sharedpreferences

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.TypeConverter
import dev.androidbroadcast.featured.enumConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

enum class CheckoutVariant { LEGACY, NEW_SINGLE_PAGE, NEW_MULTI_STEP }

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SharedPreferencesProviderConfigTest {
    private lateinit var provider: SharedPreferencesProviderConfig

    // Test parameters for different types
    private val stringParam: ConfigParam<String> = ConfigParam("test_string", "default")
    private val intParam = ConfigParam("test_int", 42)
    private val booleanParam = ConfigParam("test_boolean", false)
    private val floatParam = ConfigParam("test_float", 3.14f)
    private val longParam = ConfigParam("test_long", 123456789L)
    private val doubleParam = ConfigParam("test_double", 2.71828)

    @Before
    fun setUp() {
        val context: Application = ApplicationProvider.getApplicationContext()
        val sharedPreferences =
            context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE).apply {
                // Clear all preferences before each test
                edit { clear() }
            }
        provider = SharedPreferencesProviderConfig(sharedPreferences)
    }

    @After
    fun tearDown() =
        runTest {
            // Clean up after each test
            provider.clear()
        }

    @Test
    fun `test get returns null when value does not exist`() =
        runTest {
            // When getting a value that doesn't exist
            val result = provider.get(stringParam)

            // Then it should return null
            assertNull(result)
        }

    @Test
    fun `test set and get string value`() =
        runTest {
            // Given a string value
            val testValue = "test_string_value"

            // When setting and getting the value
            provider.set(stringParam, testValue)
            val result = provider.get(stringParam)

            // Then the value should be correctly stored and retrieved
            assertNotNull(result)
            assertEquals(testValue, result!!.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test set and get int value`() =
        runTest {
            // Given an int value
            val testValue = 999

            // When setting and getting the value
            provider.set(intParam, testValue)
            val result = provider.get(intParam)

            // Then the value should be correctly stored and retrieved
            assertNotNull(result)
            assertEquals(testValue, result!!.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test set and get boolean value`() =
        runTest {
            // Given a boolean value
            val testValue = true

            // When setting and getting the value
            provider.set(booleanParam, testValue)
            val result = provider.get(booleanParam)

            // Then the value should be correctly stored and retrieved
            assertNotNull(result)
            assertEquals(testValue, result!!.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test set and get float value`() =
        runTest {
            // Given a float value
            val testValue = 99.99f

            // When setting and getting the value
            provider.set(floatParam, testValue)
            val result = provider.get(floatParam)

            // Then the value should be correctly stored and retrieved
            assertNotNull(result)
            assertEquals(testValue, result!!.value, 0.001f)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test set and get long value`() =
        runTest {
            // Given a long value
            val testValue = 9876543210L

            // When setting and getting the value
            provider.set(longParam, testValue)
            val result = provider.get(longParam)

            // Then the value should be correctly stored and retrieved
            assertNotNull(result)
            assertEquals(testValue, result!!.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test set and get double value`() =
        runTest {
            // Given a double value
            val testValue = 123.456789

            // When setting and getting the value
            provider.set(doubleParam, testValue)
            val result = provider.get(doubleParam)

            // Then the value should be correctly stored and retrieved
            assertNotNull(result)
            assertEquals(testValue, result!!.value, 0.000001)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test remove value`() =
        runTest {
            // Given a stored value
            val testValue = "to_be_removed"
            provider.set(stringParam, testValue)

            // Verify value exists
            assertNotNull(provider.get(stringParam))

            // When removing the value
            provider.remove(stringParam.key)

            // Then the value should no longer exist
            assertNull(provider.get(stringParam))
        }

    @Test
    fun `test clear all values`() =
        runTest {
            // Given multiple stored values
            provider.set(stringParam, "test")
            provider.set(intParam, 123)
            provider.set(booleanParam, true)

            // Verify values exist
            assertNotNull(provider.get(stringParam))
            assertNotNull(provider.get(intParam))
            assertNotNull(provider.get(booleanParam))

            // When clearing all values
            provider.clear()

            // Then all values should be removed
            assertNull(provider.get(stringParam))
            assertNull(provider.get(intParam))
            assertNull(provider.get(booleanParam))
        }

    @Test
    fun `test observe value changes`() =
        runTest {
            // Given initial value
            val initialValue = "initial"
            val updatedValue = "updated"

            provider.set(stringParam, initialValue)

            // When observing the parameter
            val flow = provider.observe(stringParam)

            // Set up a list to collect emissions
            val emissions = mutableListOf<ConfigValue<String>>()

            // Collect first emission (initial value)
            val firstEmission = flow.first()
            emissions.add(firstEmission)

            // Update the value
            provider.set(stringParam, updatedValue)

            // Collect second emission (updated value)
            val secondEmission = flow.first()
            emissions.add(secondEmission)

            // Then we should have received the updated value
            assertEquals(2, emissions.size)
            assertEquals(initialValue, emissions[0].value)
            assertEquals(updatedValue, emissions[1].value)
            assertEquals(ConfigValue.Source.LOCAL, emissions[0].source)
            assertEquals(ConfigValue.Source.LOCAL, emissions[1].source)
        }

    @Test
    fun `test observe does not emit for different keys`() =
        runTest {
            // Given two different parameters
            val param1 = ConfigParam("key1", "default1")
            val param2 = ConfigParam("key2", "default2")

            // When observing param1
            val flow = provider.observe(param1)

            // Set initial value for param1
            provider.set(param1, "value1")

            // Collect first emission

            // When setting value for param2 (different key)
            provider.set(param2, "value2")

            flow.test {
                val value1 = awaitItem().value
                assertEquals("value1", value1)
            }
        }

    @Test
    fun `test overwrite existing value`() =
        runTest {
            // Given an existing value
            val originalValue = "original"
            val newValue = "new"

            provider.set(stringParam, originalValue)
            assertEquals(originalValue, provider.get(stringParam)!!.value)

            // When overwriting with a new value
            provider.set(stringParam, newValue)

            // Then the new value should be stored
            val result = provider.get(stringParam)
            assertNotNull(result)
            assertEquals(newValue, result!!.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test multiple parameters with same value type`() =
        runTest {
            // Given multiple string parameters
            val param1 = ConfigParam("string1", "default1")
            val param2 = ConfigParam("string2", "default2")

            val value1 = "value1"
            val value2 = "value2"

            // When setting different values
            provider.set(param1, value1)
            provider.set(param2, value2)

            // Then each parameter should have its own value
            assertEquals(value1, provider.get(param1)!!.value)
            assertEquals(value2, provider.get(param2)!!.value)
        }

    @Test
    fun `test exception for unsupported type`() =
        runTest {
            // Given an unsupported type parameter
            data class UnsupportedType(
                val value: String,
            )

            val unsupportedParam =
                ConfigParam("unsupported", UnsupportedType("test"))

            // When trying to set an unsupported type
            // Then it should throw an exception
            try {
                provider.set(unsupportedParam, UnsupportedType("test"))
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("Unsupported type"))
            }
        }

    // --- Enum support via TypeConverter ---

    @Test
    fun `test set and get enum value with registered converter`() =
        runTest {
            val enumParam = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.registerConverter(enumConverter<CheckoutVariant>())

            provider.set(enumParam, CheckoutVariant.NEW_SINGLE_PAGE)
            val result = provider.get(enumParam)

            assertNotNull(result)
            assertEquals(CheckoutVariant.NEW_SINGLE_PAGE, result!!.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `test enum round-trip persists by name`() =
        runTest {
            val enumParam = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.registerConverter(enumConverter<CheckoutVariant>())

            provider.set(enumParam, CheckoutVariant.NEW_MULTI_STEP)
            val result = provider.get(enumParam)

            assertEquals(CheckoutVariant.NEW_MULTI_STEP, result?.value)
        }

    @Test
    fun `test enum get returns null when not set`() =
        runTest {
            val enumParam = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.registerConverter(enumConverter<CheckoutVariant>())

            val result = provider.get(enumParam)
            assertNull(result)
        }

    @Test
    fun `test enum throws on unsupported type without converter`() =
        runTest {
            val enumParam = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            // No converter registered
            try {
                provider.set(enumParam, CheckoutVariant.LEGACY)
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("Unsupported type"))
            }
        }
}
