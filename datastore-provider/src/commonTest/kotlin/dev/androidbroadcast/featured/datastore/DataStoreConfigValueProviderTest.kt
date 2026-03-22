package dev.androidbroadcast.featured.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DataStoreConfigValueProviderTest {
    private val testScope = TestScope()

    private fun createProvider(name: String = "test_${System.currentTimeMillis()}"): DataStoreConfigValueProvider {
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { "$name.preferences_pb".toPath() },
            )
        return DataStoreConfigValueProvider(dataStore)
    }

    // --- get() ---

    @Test
    fun get_returnsNull_whenKeyNotSet() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("missing_key", "default")

            assertNull(provider.get(param))
        }

    @Test
    fun get_returnsStoredValue_afterSet() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("string_key", "default")

            provider.set(param, "hello")
            val result = provider.get(param)

            assertNotNull(result)
            assertEquals("hello", result.value)
        }

    @Test
    fun get_returnsSourceLocal_whenValueSet() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("source_check", false)

            provider.set(param, true)
            val result = provider.get(param)

            assertNotNull(result)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    // --- Type round-trips ---

    @Test
    fun roundTrip_string() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("str", "")

            provider.set(param, "hello world")

            assertEquals("hello world", provider.get(param)?.value)
        }

    @Test
    fun roundTrip_int() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("int", 0)

            provider.set(param, Int.MAX_VALUE)

            assertEquals(Int.MAX_VALUE, provider.get(param)?.value)
        }

    @Test
    fun roundTrip_long() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("long", 0L)

            provider.set(param, Long.MAX_VALUE)

            assertEquals(Long.MAX_VALUE, provider.get(param)?.value)
        }

    @Test
    fun roundTrip_float() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("float", 0f)

            provider.set(param, 3.14f)

            val result = provider.get(param)
            assertNotNull(result)
            assertEquals(3.14f, result.value, 0.0001f)
        }

    @Test
    fun roundTrip_double() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("double", 0.0)

            provider.set(param, 2.718281828)

            val result = provider.get(param)
            assertNotNull(result)
            assertEquals(2.718281828, result.value, 0.000000001)
        }

    @Test
    fun roundTrip_boolean_true() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("bool_true", false)

            provider.set(param, true)

            assertEquals(true, provider.get(param)?.value)
        }

    @Test
    fun roundTrip_boolean_false() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("bool_false", true)

            provider.set(param, false)

            assertEquals(false, provider.get(param)?.value)
        }

    // --- Unsupported type ---

    @Test
    fun set_throwsIllegalArgumentException_forUnsupportedType() =
        testScope.runTest {
            val provider = createProvider()

            data class Unsupported(
                val x: Int,
            )

            val param = ConfigParam("bad_type", Unsupported(1))

            assertFailsWith<IllegalArgumentException> {
                provider.set(param, Unsupported(2))
            }
        }

    @Test
    fun get_throwsIllegalArgumentException_forUnsupportedType() =
        testScope.runTest {
            val provider = createProvider()

            data class Unsupported(
                val x: Int,
            )

            val param = ConfigParam("bad_type_get", Unsupported(1))

            assertFailsWith<IllegalArgumentException> {
                provider.get(param)
            }
        }

    // --- observe() — first emission only (safe on all platforms) ---

    @Test
    fun observe_emitsCurrentValue_onSubscription() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("obs_key", "default")
            provider.set(param, "initial")

            provider.observe(param).test {
                val emission = awaitItem()
                assertEquals("initial", emission.value)
                assertEquals(ConfigValue.Source.LOCAL, emission.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observe_emitsDefault_whenNotSet() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("obs_default", "my_default")

            provider.observe(param).test {
                val emission = awaitItem()
                assertEquals("my_default", emission.value)
                assertEquals(ConfigValue.Source.DEFAULT, emission.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- resetOverride ---

    @Test
    fun resetOverride_makesGetReturnNull() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("reset_key", "default")

            provider.set(param, "set_value")
            assertNotNull(provider.get(param))

            provider.resetOverride(param)

            assertNull(provider.get(param))
        }

    // --- clear ---

    @Test
    fun clear_removesAllValues() =
        testScope.runTest {
            val provider = createProvider()
            val stringParam = ConfigParam("clear_str", "default")
            val intParam = ConfigParam("clear_int", 0)
            val boolParam = ConfigParam("clear_bool", false)

            provider.set(stringParam, "value")
            provider.set(intParam, 42)
            provider.set(boolParam, true)

            provider.clear()

            assertNull(provider.get(stringParam))
            assertNull(provider.get(intParam))
            assertNull(provider.get(boolParam))
        }

    // --- Persistence ---

    @Test
    fun value_persistsAfterOtherKeyReset() =
        testScope.runTest {
            val provider = createProvider()
            val persistedParam = ConfigParam("persistent_key", "default")
            val otherParam = ConfigParam("other_key", "other_default")

            provider.set(persistedParam, "persisted_value")
            provider.set(otherParam, "other_value")

            provider.resetOverride(otherParam)

            val result = provider.get(persistedParam)
            assertNotNull(result)
            assertEquals("persisted_value", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun value_isReadableAfterMultipleSets() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("multi_set_key", "default")

            provider.set(param, "first")
            provider.set(param, "second")
            provider.set(param, "third")

            val result = provider.get(param)
            assertNotNull(result)
            assertEquals("third", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }
}
