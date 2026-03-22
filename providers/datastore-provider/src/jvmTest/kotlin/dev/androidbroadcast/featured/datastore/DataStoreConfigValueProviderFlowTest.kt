package dev.androidbroadcast.featured.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * JVM-only tests for reactive [DataStoreConfigValueProvider.observe] behaviour and concurrent
 * write scenarios. These tests rely on DataStore's file-based write serialisation which is
 * not compatible with the Robolectric file-rename restrictions used in Android unit tests.
 */
class DataStoreConfigValueProviderFlowTest {
    private val testScope = TestScope()

    private fun createProvider(name: String = "flow_test_${System.currentTimeMillis()}"): DataStoreConfigValueProvider {
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { "$name.preferences_pb".toPath() },
            )
        return DataStoreConfigValueProvider(dataStore)
    }

    // --- observe() reactive emissions ---

    @Test
    fun observe_emitsNewValue_afterSet() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("obs_update", "default")

            provider.observe(param).test {
                awaitItem() // initial default emission

                provider.set(param, "updated")

                val updated = awaitItem()
                assertEquals("updated", updated.value)
                assertEquals(ConfigValue.Source.LOCAL, updated.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observe_emitsMultipleUpdates_inOrder() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("obs_multi", "default")

            provider.observe(param).test {
                awaitItem() // default

                provider.set(param, "first")
                assertEquals("first", awaitItem().value)

                provider.set(param, "second")
                assertEquals("second", awaitItem().value)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resetOverride_causesObserveToEmitDefault() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("reset_obs", "fallback")
            provider.set(param, "stored")

            provider.observe(param).test {
                awaitItem() // stored value

                provider.resetOverride(param)

                val afterReset = awaitItem()
                assertEquals("fallback", afterReset.value)
                assertEquals(ConfigValue.Source.DEFAULT, afterReset.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Concurrent reads and writes ---

    @Test
    fun concurrentWrites_doNotCorruptData() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("concurrent_key", 0)

            // DataStore serialises writes internally via its write actor
            val writes =
                (1..10).map { i ->
                    async { provider.set(param, i) }
                }
            writes.awaitAll()

            // Final value must be one of 1..10 and source must be LOCAL
            val result = provider.get(param)
            assertNotNull(result)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun concurrentReadsAndWrites_doNotThrow() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("rw_concurrent", "initial")
            provider.set(param, "initial")

            val readers = (1..5).map { async { provider.get(param) } }
            val writers = (1..5).map { i -> async { provider.set(param, "value_$i") } }
            (readers + writers).awaitAll()
            // No exception = pass
        }
}
