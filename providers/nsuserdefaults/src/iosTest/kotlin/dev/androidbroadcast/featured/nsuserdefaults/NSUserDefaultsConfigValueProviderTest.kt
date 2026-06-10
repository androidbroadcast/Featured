package dev.androidbroadcast.featured.nsuserdefaults

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun getReturnsNullWhenKeyNotSet() =
        runTest {
            val param = ConfigParam("missing_key", false)
            assertNull(provider.get(param))
        }

    @Test
    fun getReturnsStoredBooleanValue() =
        runTest {
            val param = ConfigParam("bool_key", false)
            provider.set(param, true)
            val result = provider.get(param)
            assertEquals(ConfigValue(true, ConfigValue.Source.LOCAL), result)
        }

    @Test
    fun getReturnsStoredIntValue() =
        runTest {
            val param = ConfigParam("int_key", 0)
            provider.set(param, 42)
            val result = provider.get(param)
            assertEquals(ConfigValue(42, ConfigValue.Source.LOCAL), result)
        }

    @Test
    fun getReturnsStoredLongValue() =
        runTest {
            val param = ConfigParam("long_key", 0L)
            provider.set(param, 123456789L)
            val result = provider.get(param)
            assertEquals(ConfigValue(123456789L, ConfigValue.Source.LOCAL), result)
        }

    @Test
    fun getReturnsStoredDoubleValue() =
        runTest {
            val param = ConfigParam("double_key", 0.0)
            provider.set(param, 3.14)
            val result = provider.get(param)
            assertEquals(ConfigValue(3.14, ConfigValue.Source.LOCAL), result)
        }

    @Test
    fun getReturnsStoredFloatValue() =
        runTest {
            val param = ConfigParam("float_key", 0f)
            provider.set(param, 2.5f)
            val result = provider.get(param)
            assertEquals(ConfigValue(2.5f, ConfigValue.Source.LOCAL), result)
        }

    @Test
    fun getReturnsStoredStringValue() =
        runTest {
            val param = ConfigParam("string_key", "")
            provider.set(param, "hello")
            val result = provider.get(param)
            assertEquals(ConfigValue("hello", ConfigValue.Source.LOCAL), result)
        }

    // --- reserved key protection ---

    @Test
    fun getWithReservedKeyThrows() =
        runTest {
            val param = ConfigParam(NSUserDefaultsConfigValueProvider.RESERVED_INDEX_KEY, "default")
            assertFailsWith<IllegalArgumentException> {
                provider.get(param)
            }
        }

    @Test
    fun setWithReservedKeyThrows() =
        runTest {
            val param = ConfigParam(NSUserDefaultsConfigValueProvider.RESERVED_INDEX_KEY, "default")
            assertFailsWith<IllegalArgumentException> {
                provider.set(param, "value")
            }
        }

    @Test
    fun reservedKeyIsInvisibleViaObserve() =
        runTest {
            // The reserved key must not surface as a provider value — observe emits DEFAULT
            val param = ConfigParam(NSUserDefaultsConfigValueProvider.RESERVED_INDEX_KEY, "my_default")
            // Manually write something at the reserved key to simulate index presence
            val defaults = NSUserDefaults(suiteName = suiteName)
            defaults.setObject(listOf("some_key"), forKey = NSUserDefaultsConfigValueProvider.RESERVED_INDEX_KEY)

            provider.observe(param).test {
                val emission = awaitItem()
                // get() throws for the reserved key, so observe must emit DEFAULT (error path)
                assertEquals("my_default", emission.value)
                assertEquals(ConfigValue.Source.DEFAULT, emission.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resetOverrideWithReservedKeyThrows() =
        runTest {
            val param = ConfigParam(NSUserDefaultsConfigValueProvider.RESERVED_INDEX_KEY, "default")
            assertFailsWith<IllegalArgumentException> {
                provider.resetOverride(param)
            }
        }

    // --- resetOverride ---

    @Test
    fun resetOverrideMakesGetReturnNull() =
        runTest {
            val param = ConfigParam("reset_key", false)
            provider.set(param, true)
            provider.resetOverride(param)
            assertNull(provider.get(param))
        }

    // --- clear ---

    @Test
    fun clearRemovesProviderOwnedValues() =
        runTest {
            val boolParam = ConfigParam("clear_bool", false)
            val strParam = ConfigParam("clear_str", "")
            provider.set(boolParam, true)
            provider.set(strParam, "value")
            provider.clear()
            assertNull(provider.get(boolParam))
            assertNull(provider.get(strParam))
        }

    @Test
    fun clearDoesNotTouchForeignKeyInSameSuite() =
        runTest {
            // A foreign key written directly into the same suite must survive clear().
            val foreignKey = "foreign.key.written.directly"
            val defaults = NSUserDefaults(suiteName = suiteName)
            defaults.setObject("foreign_value", forKey = foreignKey)

            val ownedParam = ConfigParam("owned_key", "default")
            provider.set(ownedParam, "owned_value")

            provider.clear()

            // Provider-owned key is gone
            assertNull(provider.get(ownedParam))
            // Foreign key survives — clear() only removes index-tracked keys
            assertEquals("foreign_value", defaults.objectForKey(foreignKey))
        }

    @Test
    fun clearNotifiesActiveObserversWithDefault() =
        runTest {
            val param = ConfigParam("obs_clear_key", "my_default")
            provider.set(param, "stored")

            provider.observe(param).test {
                // Initial LOCAL emission
                val initial = awaitItem()
                assertEquals("stored", initial.value)
                assertEquals(ConfigValue.Source.LOCAL, initial.source)

                provider.clear()

                val afterClear = awaitItem()
                assertEquals("my_default", afterClear.value)
                assertEquals(ConfigValue.Source.DEFAULT, afterClear.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearIndexSurvivesAcrossProviderInstances() =
        runTest {
            // Set a value via the original provider so the key is indexed
            val param = ConfigParam("persist_key", "default")
            provider.set(param, "value")

            // Create a second provider over the same suite (simulates app restart)
            val provider2 = NSUserDefaultsConfigValueProvider(suiteName = suiteName)

            // clear() on provider2 must still find and remove the key
            provider2.clear()

            // Both provider instances agree the key is gone
            assertNull(provider.get(param))
            assertNull(provider2.get(param))
        }

    // --- observe ---

    @Test
    fun observeEmitsCurrentValueImmediatelyWhenSet() =
        runTest {
            val param = ConfigParam("observe_key", false)
            provider.set(param, true)
            val emitted = provider.observe(param).first()
            assertEquals(ConfigValue(true, ConfigValue.Source.LOCAL), emitted)
        }

    @Test
    fun observeEmitsUpdatedValueAfterSet() =
        runTest {
            val param = ConfigParam("observe_update_key", 0)
            provider.set(param, 1)

            provider.observe(param).test {
                assertEquals(ConfigValue(1, ConfigValue.Source.LOCAL), awaitItem())

                provider.set(param, 99)
                assertEquals(ConfigValue(99, ConfigValue.Source.LOCAL), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeEmitsDefaultImmediatelyWhenKeyHasNeverBeenWritten() =
        runTest {
            val param = ConfigParam("never_written_bool", false)

            provider.observe(param).test {
                val emission = awaitItem()
                assertEquals(false, emission.value)
                assertEquals(ConfigValue.Source.DEFAULT, emission.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeEmitsDefaultAfterResetOverrideWhenValueCleared() =
        runTest {
            val param = ConfigParam("reset_obs_key", false)
            provider.set(param, true)

            provider.observe(param).test {
                val initial = awaitItem()
                assertEquals(true, initial.value)
                assertEquals(ConfigValue.Source.LOCAL, initial.source)

                provider.resetOverride(param)
                val afterReset = awaitItem()
                assertEquals(false, afterReset.value)
                assertEquals(ConfigValue.Source.DEFAULT, afterReset.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- index atomicity ---

    @Test
    fun parallelSetsAllKeysAppearedInIndex() =
        runTest {
            val keyCount = 20
            val params = (0 until keyCount).map { i -> ConfigParam("parallel_key_$i", false) }

            coroutineScope {
                params
                    .map { param ->
                        async { provider.set(param, true) }
                    }.forEach { it.await() }
            }

            // Every key set concurrently must be present in the index so clear() can remove them.
            params.forEach { param ->
                assertEquals(
                    expected = ConfigValue(true, ConfigValue.Source.LOCAL),
                    actual = provider.get(param),
                )
            }
            provider.clear()
            params.forEach { param ->
                assertNull(provider.get(param))
            }
        }
}
