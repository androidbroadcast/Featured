package dev.androidbroadcast.featured.javaprefs

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.TypeConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

enum class Variant { A, B, C }

class JavaPreferencesConfigValueProviderTest {
    private lateinit var node: Preferences
    private lateinit var provider: JavaPreferencesConfigValueProvider

    private val stringParam = ConfigParam("test_string", "default")
    private val intParam = ConfigParam("test_int", 42)
    private val booleanParam = ConfigParam("test_boolean", false)
    private val floatParam = ConfigParam("test_float", 3.14f)
    private val longParam = ConfigParam("test_long", 123456789L)
    private val doubleParam = ConfigParam("test_double", 2.71828)

    @BeforeTest
    fun setUp() {
        node = Preferences.userRoot().node("featured-test-${UUID.randomUUID()}")
        provider = JavaPreferencesConfigValueProvider(node)
    }

    @AfterTest
    fun tearDown() =
        runTest {
            node.removeNode()
        }

    @Test
    fun `get returns null when value does not exist`() =
        runTest {
            assertNull(provider.get(stringParam))
        }

    @Test
    fun `set and get string value`() =
        runTest {
            provider.set(stringParam, "hello")
            val result = provider.get(stringParam)
            assertNotNull(result)
            assertEquals("hello", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `set and get int value`() =
        runTest {
            provider.set(intParam, 999)
            val result = provider.get(intParam)
            assertNotNull(result)
            assertEquals(999, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `set and get boolean value`() =
        runTest {
            provider.set(booleanParam, true)
            val result = provider.get(booleanParam)
            assertNotNull(result)
            assertEquals(true, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `set and get float value`() =
        runTest {
            provider.set(floatParam, 99.99f)
            val result = provider.get(floatParam)
            assertNotNull(result)
            assertEquals(99.99f, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `set and get long value`() =
        runTest {
            provider.set(longParam, 9876543210L)
            val result = provider.get(longParam)
            assertNotNull(result)
            assertEquals(9876543210L, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `set and get double value`() =
        runTest {
            provider.set(doubleParam, 123.456789)
            val result = provider.get(doubleParam)
            assertNotNull(result)
            assertEquals(123.456789, result.value, 0.000001)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `resetOverride removes the value`() =
        runTest {
            provider.set(stringParam, "stored")
            assertNotNull(provider.get(stringParam))
            provider.resetOverride(stringParam)
            assertNull(provider.get(stringParam))
        }

    @Test
    fun `clear removes all values`() =
        runTest {
            provider.set(stringParam, "test")
            provider.set(intParam, 123)
            provider.set(booleanParam, true)

            provider.clear()

            assertNull(provider.get(stringParam))
            assertNull(provider.get(intParam))
            assertNull(provider.get(booleanParam))
        }

    @Test
    fun `overwrite existing value`() =
        runTest {
            provider.set(stringParam, "original")
            provider.set(stringParam, "updated")
            val result = provider.get(stringParam)
            assertNotNull(result)
            assertEquals("updated", result.value)
        }

    @Test
    fun `multiple params with same type are independent`() =
        runTest {
            val param1 = ConfigParam("key1", "default1")
            val param2 = ConfigParam("key2", "default2")

            provider.set(param1, "value1")
            provider.set(param2, "value2")

            assertEquals("value1", provider.get(param1)!!.value)
            assertEquals("value2", provider.get(param2)!!.value)
        }

    @Test
    fun `observe emits current value then updates`() =
        runTest {
            provider.set(stringParam, "initial")

            provider.observe(stringParam).test {
                assertEquals("initial", awaitItem().value)

                provider.set(stringParam, "updated")
                assertEquals("updated", awaitItem().value)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe does not emit for different keys`() =
        runTest {
            val param1 = ConfigParam("key1", "default1")
            val param2 = ConfigParam("key2", "default2")

            provider.set(param1, "value1")

            provider.observe(param1).test {
                assertEquals("value1", awaitItem().value)

                provider.set(param2, "value2")
                // No new emission for param1
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe emits null-skipped on resetOverride`() =
        runTest {
            provider.set(stringParam, "present")

            // After resetOverride, observe should not emit (null is filtered)
            provider.observe(stringParam).test {
                assertEquals("present", awaitItem().value)

                provider.resetOverride(stringParam)
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unsupported type throws IllegalArgumentException`() =
        runTest {
            data class Custom(
                val x: String,
            )
            val param = ConfigParam("custom", Custom("default"))

            assertFailsWith<IllegalArgumentException> {
                provider.set(param, Custom("value"))
            }
        }

    @Test
    fun `set and get enum value with registered converter`() =
        runTest {
            val enumParam = ConfigParam("variant", Variant.A)
            provider.registerConverter<Variant>(
                object : TypeConverter<Variant> {
                    override fun toString(value: Variant): String = value.name

                    override fun fromString(value: String): Variant = Variant.valueOf(value)
                },
            )

            provider.set(enumParam, Variant.B)
            val result = provider.get(enumParam)

            assertNotNull(result)
            assertEquals(Variant.B, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `enum get returns null when not set`() =
        runTest {
            val enumParam = ConfigParam("variant", Variant.A)
            provider.registerConverter<Variant>(
                object : TypeConverter<Variant> {
                    override fun toString(value: Variant): String = value.name

                    override fun fromString(value: String): Variant = Variant.valueOf(value)
                },
            )

            assertNull(provider.get(enumParam))
        }

    @Test
    fun `clear via LocalConfigValueProvider interface removes all values`() =
        runTest {
            val localProvider: dev.androidbroadcast.featured.LocalConfigValueProvider = provider
            localProvider.set(stringParam, "value1")
            localProvider.set(intParam, 99)

            localProvider.clear()

            assertNull(localProvider.get(stringParam))
            assertNull(localProvider.get(intParam))
        }

    @Test
    fun `observe emits nothing when value never set`() =
        runTest {
            provider.observe(stringParam).test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
