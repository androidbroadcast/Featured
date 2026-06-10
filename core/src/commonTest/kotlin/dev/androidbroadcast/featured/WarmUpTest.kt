package dev.androidbroadcast.featured

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [ConfigValues.warmUp] and the automatic warm-set refresh that fires inside
 * [ConfigValues.initialize] and [ConfigValues.fetch].
 */
class WarmUpTest {
    // ---------------------------------------------------------------------------
    // Shared test helpers
    // ---------------------------------------------------------------------------

    /** Remote provider backed by a mutable map; values survive across fetch() calls. */
    private class MutableRemoteProvider(
        initialValues: Map<String, Any> = emptyMap(),
    ) : RemoteConfigValueProvider,
        InitializableConfigValueProvider {
        private val activeValues = initialValues.toMutableMap()

        override suspend fun initialize() {
            // no-op: values are loaded via constructor; already present in activeValues
        }

        override suspend fun fetch(activate: Boolean) {
            // no-op: test updates activeValues directly to simulate new remote values
        }

        fun put(
            key: String,
            value: Any,
        ) {
            activeValues[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
            activeValues[param.key]?.let { value ->
                ConfigValue(value as T, ConfigValue.Source.REMOTE)
            }
    }

    /** Remote provider that throws on get() to exercise the error-recovery path. */
    private class FailingRemoteProvider : RemoteConfigValueProvider {
        override suspend fun fetch(activate: Boolean) = Unit

        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = throw RuntimeException("provider unavailable")
    }

    // ---------------------------------------------------------------------------
    // (a) initialize() + warmUp() → getValueCached returns REMOTE values
    // ---------------------------------------------------------------------------

    @Test
    fun initializeAndWarmUpGetValueCachedReturnsRemoteSourceForWarmedParams() =
        runTest {
            val param = ConfigParam("feat_a", "default")
            val remote = MutableRemoteProvider(mapOf("feat_a" to "from_cache"))
            val configValues = ConfigValues(remoteProvider = remote)

            // Before warmUp: snapshot is cold, returns DEFAULT.
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(param).source)

            configValues.initialize()
            configValues.warmUp(listOf(param))

            val cached = configValues.getValueCached(param)
            assertEquals("from_cache", cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // (b) fetch() after warmUp() → getValueCached reflects new remote values
    //     without any intervening getValue / observe calls
    // ---------------------------------------------------------------------------

    @Test
    fun fetchAfterWarmUpGetValueCachedReflectsUpdatedRemoteValuesWithoutGetValue() =
        runTest {
            val param = ConfigParam("feat_b", "default")
            val remote = MutableRemoteProvider(mapOf("feat_b" to "v1"))
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.warmUp(listOf(param))
            assertEquals("v1", configValues.getValueCached(param).value)

            // Simulate a remote update — no getValue or observe called between fetch calls.
            remote.put("feat_b", "v2")
            configValues.fetch()

            val cached = configValues.getValueCached(param)
            assertEquals("v2", cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // (c) param NOT in warmUp → getValueCached still returns DEFAULT after fetch
    // ---------------------------------------------------------------------------

    @Test
    fun paramNotInWarmUpGetValueCachedReturnsDefaultAfterFetch() =
        runTest {
            val warmParam = ConfigParam("warm", "default_warm")
            val coldParam = ConfigParam("cold", "default_cold")
            val remote = MutableRemoteProvider(mapOf("warm" to "warm_v", "cold" to "cold_v"))
            val configValues = ConfigValues(remoteProvider = remote)

            // Register only warmParam in the warm-set.
            configValues.warmUp(listOf(warmParam))
            configValues.fetch()

            // warmParam was refreshed by fetch.
            assertEquals("warm_v", configValues.getValueCached(warmParam).value)

            // coldParam was never warmed — snapshot slot is absent, returns DEFAULT.
            val coldCached = configValues.getValueCached(coldParam)
            assertEquals("default_cold", coldCached.value)
            assertEquals(ConfigValue.Source.DEFAULT, coldCached.source)
        }

    // ---------------------------------------------------------------------------
    // (d) parallel warmUp of N params — batch write loses no entries
    // ---------------------------------------------------------------------------

    @Test
    fun warmUpOf50ParamsAllEntriesPresentInSnapshotAfterBatchWrite() =
        runTest {
            val paramCount = 50
            val params =
                (0 until paramCount).map { i -> ConfigParam("flag_$i", "default_$i") }
            val remoteValues = params.associate { p -> p.key to "value_${p.key}" }
            val remote = MutableRemoteProvider(remoteValues)
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.warmUp(params)

            params.forEach { param ->
                val cached = configValues.getValueCached(param)
                assertEquals("value_${param.key}", cached.value, "missing entry for ${param.key}")
                assertEquals(ConfigValue.Source.REMOTE, cached.source)
            }
        }

    // ---------------------------------------------------------------------------
    // (e) warmUp with failing provider → param warmed to defaultValue, error reported
    // ---------------------------------------------------------------------------

    @Test
    fun warmUpWithFailingProviderParamResolvesToDefaultValueAndErrorIsReported() =
        runTest {
            val param = ConfigParam("feat_e", "the_default")
            val capturedErrors = mutableListOf<Throwable>()
            val configValues =
                ConfigValues(
                    remoteProvider = FailingRemoteProvider(),
                    onProviderError = { e -> capturedErrors += e },
                )

            configValues.warmUp(listOf(param))

            // Provider failed: getValue returns a DEFAULT-sourced ConfigValue from the fallback
            // path. refreshWarmSet filters DEFAULT-sourced results out before the batch commit,
            // so no snapshot slot is written. getValueCached falls back to ConfigParam.defaultValue
            // via the cold-read path, returning DEFAULT source.
            val cached = configValues.getValueCached(param)
            assertEquals("the_default", cached.value)
            assertEquals(ConfigValue.Source.DEFAULT, cached.source)
            // Error was reported to the handler.
            assertTrue(capturedErrors.isNotEmpty(), "expected at least one provider error")
        }

    // ---------------------------------------------------------------------------
    // (f) fetch with active observe collectors — warm-refresh and observe coexist
    // ---------------------------------------------------------------------------

    @Test
    fun fetchWithActiveObserverWarmRefreshDoesNotRegressSnapshotToDefault() =
        runTest {
            val param = ConfigParam("feat_f", false)
            val remote = MutableRemoteProvider(mapOf("feat_f" to true))
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.warmUp(listOf(param))

            // Snapshot is warm: should be true / REMOTE.
            assertEquals(true, configValues.getValueCached(param).value)
            assertEquals(ConfigValue.Source.REMOTE, configValues.getValueCached(param).source)

            val collectedValues = mutableListOf<ConfigValue<Boolean>>()
            val job =
                launch {
                    configValues.observe(param).collect { collectedValues += it }
                }

            // Simulate a remote update and fetch.
            remote.put("feat_f", true)
            configValues.fetch()

            // After fetch, snapshot must still be REMOTE (not regressed to DEFAULT).
            val afterFetch = configValues.getValueCached(param)
            assertEquals(true, afterFetch.value)
            assertEquals(ConfigValue.Source.REMOTE, afterFetch.source)

            // No DEFAULT-sourced value must have been emitted to observers during the fetch
            // warm-refresh cycle — the snapshot was already warm and must stay warm.
            assertFalse(
                collectedValues.any { it.source == ConfigValue.Source.DEFAULT },
                "observer received a DEFAULT-sourced emission during warm fetch; snapshot regressed",
            )

            job.cancel()
        }

    // ---------------------------------------------------------------------------
    // (g) warmUp idempotence — second call refreshes values, no duplication
    // ---------------------------------------------------------------------------

    @Test
    fun warmUpCalledTwiceWithSameParamsIdempotentSecondCallRefreshesValues() =
        runTest {
            val param = ConfigParam("feat_g", "default")
            val remote = MutableRemoteProvider(mapOf("feat_g" to "v1"))
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.warmUp(listOf(param))
            assertEquals("v1", configValues.getValueCached(param).value)

            // Update remote value; second warmUp should pick it up and not duplicate entries.
            remote.put("feat_g", "v2")
            configValues.warmUp(listOf(param))

            assertEquals("v2", configValues.getValueCached(param).value)
            assertEquals(ConfigValue.Source.REMOTE, configValues.getValueCached(param).source)
        }

    // ---------------------------------------------------------------------------
    // (h) warmUp BEFORE initialize — initialize's internal refresh warms from cache
    // ---------------------------------------------------------------------------

    @Test
    fun warmUpBeforeInitializeRefreshPopulatesSnapshotWithRemoteValues() =
        runTest {
            val param = ConfigParam("feat_h", "default_h")
            val remote = MutableRemoteProvider(mapOf("feat_h" to "cached_remote"))
            val configValues = ConfigValues(remoteProvider = remote)

            // Register the param in the warm-set before initialize() is called.
            configValues.warmUp(listOf(param))

            // Simulate a second round: clear snapshot knowledge by verifying it is already warm,
            // then call initialize() to exercise its internal refreshWarmSet path.
            assertEquals("cached_remote", configValues.getValueCached(param).value)

            // Update the provider to a new value; initialize() must re-resolve from the provider.
            remote.put("feat_h", "refreshed_remote")
            configValues.initialize()

            // refreshWarmSet inside initialize() must resolve the registered warm-set param
            // and write the freshly resolved REMOTE value into the snapshot.
            val cached = configValues.getValueCached(param)
            assertEquals("refreshed_remote", cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // mergeWarmResults — unit tests for the merge rule
    // ---------------------------------------------------------------------------

    @Test
    fun mergeWarmResultsLocalCurrentKeptWhenResolvedIsNonLocal() {
        val key = "flag"
        val localValue = ConfigValue("local_override", ConfigValue.Source.LOCAL)
        val remoteValue = ConfigValue("remote_value", ConfigValue.Source.REMOTE)

        val current = mapOf(key to localValue)
        val resolved = listOf(key to remoteValue as ConfigValue<*>)

        val result = mergeWarmResults(current, resolved)

        // LOCAL current must win: an override landed mid-flight; resolved is non-LOCAL.
        assertEquals(localValue, result[key])
    }

    @Test
    fun mergeWarmResultsNonLocalCurrentReplacedByFreshResolved() {
        val key = "flag"
        val staleRemote = ConfigValue("stale", ConfigValue.Source.REMOTE)
        val freshRemote = ConfigValue("fresh", ConfigValue.Source.REMOTE)

        val current = mapOf(key to staleRemote)
        val resolved = listOf(key to freshRemote as ConfigValue<*>)

        val result = mergeWarmResults(current, resolved)

        // non-LOCAL current must be overwritten with the fresh resolved value.
        assertEquals(freshRemote, result[key])
    }

    @Test
    fun mergeWarmResultsAbsentCurrentSlotFilledByResolved() {
        val key = "flag"
        val remoteValue = ConfigValue("remote_value", ConfigValue.Source.REMOTE)

        val current = emptyMap<String, ConfigValue<*>>()
        val resolved = listOf(key to remoteValue as ConfigValue<*>)

        val result = mergeWarmResults(current, resolved)

        assertEquals(remoteValue, result[key])
    }

    @Test
    fun mergeWarmResultsDefaultSourcedResolvedEntryIsNotWrittenIntoSnapshot() {
        val key = "flag"
        val defaultValue = ConfigValue("default_val", ConfigValue.Source.DEFAULT)

        val current = emptyMap<String, ConfigValue<*>>()
        val resolved = listOf(key to defaultValue as ConfigValue<*>)

        val result = mergeWarmResults(current, resolved)

        // DEFAULT must never be persisted into the snapshot — slot must remain absent.
        assertFalse(result.containsKey(key), "DEFAULT-sourced entry must not be written into the snapshot")
    }

    // ---------------------------------------------------------------------------
    // (i) initialize() provider throws → exception propagates, snapshot stays cold
    // ---------------------------------------------------------------------------

    @Test
    fun initializeProviderThrowsExceptionPropagatesAndSnapshotStaysColdForWarmedParam() =
        runTest {
            val param = ConfigParam("feat_i", "cold_default")
            val failingProvider =
                object : RemoteConfigValueProvider, InitializableConfigValueProvider {
                    override suspend fun initialize() = throw RuntimeException("init failed")

                    override suspend fun fetch(activate: Boolean) = Unit

                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = null
                }
            val configValues = ConfigValues(remoteProvider = failingProvider)

            // Register the param before initialize() is attempted.
            configValues.warmUp(listOf(param))
            // warmUp itself succeeded (get() returns null → DEFAULT, not written); snapshot is cold.
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(param).source)

            // initialize() must propagate the exception from the provider's initialize().
            assertFailsWith<RuntimeException>("init failed") {
                configValues.initialize()
            }

            // Warm-set refresh was skipped — snapshot must still be cold.
            val cached = configValues.getValueCached(param)
            assertEquals("cold_default", cached.value)
            assertEquals(ConfigValue.Source.DEFAULT, cached.source)
        }

    // ---------------------------------------------------------------------------
    // (j) mixed warm-up: provider succeeds for param1, throws for param2
    //     → param1 warmed (REMOTE), param2 absent from snapshot (DEFAULT fallback),
    //        exactly one error reported
    // ---------------------------------------------------------------------------

    @Test
    fun warmUpMixedOutcomeParam1WarmRemoteParam2AbsentFromSnapshotOneErrorReported() =
        runTest {
            val param1 = ConfigParam("feat_j1", "default_j1")
            val param2 = ConfigParam("feat_j2", "default_j2")
            val capturedErrors = mutableListOf<Throwable>()

            val mixedProvider =
                object : RemoteConfigValueProvider {
                    override suspend fun fetch(activate: Boolean) = Unit

                    @Suppress("UNCHECKED_CAST")
                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
                        when (param.key) {
                            "feat_j1" -> ConfigValue("remote_j1" as T, ConfigValue.Source.REMOTE)
                            "feat_j2" -> throw RuntimeException("j2 unavailable")
                            else -> null
                        }
                }

            val configValues =
                ConfigValues(
                    remoteProvider = mixedProvider,
                    onProviderError = { capturedErrors += it },
                )

            configValues.warmUp(listOf(param1, param2))

            // param1: provider succeeded → REMOTE in snapshot.
            val cached1 = configValues.getValueCached(param1)
            assertEquals("remote_j1", cached1.value)
            assertEquals(ConfigValue.Source.REMOTE, cached1.source)

            // param2: provider threw → DEFAULT-sourced value filtered out → slot absent → cold read.
            val cached2 = configValues.getValueCached(param2)
            assertEquals("default_j2", cached2.value)
            assertEquals(ConfigValue.Source.DEFAULT, cached2.source)

            // Exactly one error was reported (for param2).
            assertEquals(1, capturedErrors.size)
            assertEquals("j2 unavailable", capturedErrors[0].message)
        }
}
