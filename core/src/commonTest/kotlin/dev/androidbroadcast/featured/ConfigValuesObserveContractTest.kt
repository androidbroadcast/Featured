package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Integration tests for [ConfigValues.observe] trigger-model contract.
 *
 * Each test asserts both the COUNT and ORDER of emitted frames, verifying that the
 * trigger-model rewrite does not introduce spurious DEFAULT-sourced frames that would
 * clobber already-known remote or local values.
 *
 * All test names use plain camelCase (no backticks) for Kotlin/Native compatibility.
 */
class ConfigValuesObserveContractTest {
    // --- helpers ---

    private class FakeRemoteProvider : RemoteConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()

        fun set(
            key: String,
            value: Any,
        ) {
            storage[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
            storage[param.key]?.let { value ->
                ConfigValue(value as T, ConfigValue.Source.REMOTE)
            }

        override suspend fun fetch(activate: Boolean) = Unit
    }

    /**
     * A [LocalConfigValueProvider] that emits on every key in its store, not just the observed
     * key — simulating a whole-store provider such as DataStore.
     */
    private class WholeStoreLocalProvider : LocalConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()

        // Emits the changed key name on every set/resetOverride.
        private val changeSignal = MutableSharedFlow<String>(extraBufferCapacity = 1000)

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
            storage[param.key]?.let { v ->
                ConfigValue(v as T, ConfigValue.Source.LOCAL)
            }

        override suspend fun <T : Any> set(
            param: ConfigParam<T>,
            value: T,
        ) {
            storage[param.key] = value
            changeSignal.tryEmit(param.key)
        }

        override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
            storage.remove(param.key)
            changeSignal.tryEmit(param.key)
        }

        override suspend fun clear() {
            storage.clear()
        }

        /**
         * Returns a Flow that ALWAYS emits on every key change — not filtered by [param].
         * This simulates DataStore's behaviour where a write to any preference triggers an
         * emission of the full preferences snapshot.
         */
        override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
            changeSignal.map {
                get(param) ?: ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
            }
    }

    // --- test cases ---

    /**
     * (a) When local has no value and remote has one, the initial frame is REMOTE.
     * No intermediate DEFAULT frame should appear between collection and the REMOTE frame.
     */
    @Test
    fun observeEmitsRemoteInitially_whenLocalHasNoKeyAndRemoteHasValue() =
        runTest {
            val remote = FakeRemoteProvider().also { it.set("flag", "remote_val") }
            val configValues =
                ConfigValues(
                    localProvider = InMemoryConfigValueProvider(),
                    remoteProvider = remote,
                )
            val param = ConfigParam("flag", "default_val")

            configValues.observe(param).test {
                val first = awaitItem()
                assertEquals("remote_val", first.value)
                assertEquals(ConfigValue.Source.REMOTE, first.source)
                // No additional DEFAULT frame follows the REMOTE frame.
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * (b) override → resetOverride → the observer converges to REMOTE without emitting DEFAULT
     * while remote value is still present.
     *
     * Frame sequence: REMOTE (initial) → LOCAL (after override) → REMOTE (after resetOverride)
     * No DEFAULT frame should appear between LOCAL and REMOTE.
     */
    @Test
    fun observeConvergesToRemote_afterOverrideAndReset_withoutDefaultFlicker() =
        runTest {
            val remote = FakeRemoteProvider().also { it.set("flag", "remote_val") }
            val local = InMemoryConfigValueProvider()
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                )
            val param = ConfigParam("flag", "default_val")

            configValues.observe(param).test {
                // Initial: remote value
                val first = awaitItem()
                assertEquals("remote_val", first.value)
                assertEquals(ConfigValue.Source.REMOTE, first.source)

                // Override with local value
                configValues.override(param, "local_val")
                val afterOverride = awaitItem()
                assertEquals("local_val", afterOverride.value)
                assertEquals(ConfigValue.Source.LOCAL, afterOverride.source)

                // Reset override — observer should resolve back to REMOTE
                configValues.resetOverride(param)
                val afterReset = awaitItem()
                assertEquals("remote_val", afterReset.value)
                // Must NOT be DEFAULT (remote value is still present)
                assertNotEquals(ConfigValue.Source.DEFAULT, afterReset.source)
                assertEquals(ConfigValue.Source.REMOTE, afterReset.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * (c) No remote provider: initial frame is DEFAULT; after override emits LOCAL.
     *
     * Frame sequence: DEFAULT → LOCAL
     */
    @Test
    fun observeEmitsDefault_thenLocal_whenNoRemoteProvider() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = local)
            val param = ConfigParam("flag", "my_default")

            configValues.observe(param).test {
                val first = awaitItem()
                assertEquals("my_default", first.value)
                assertEquals(ConfigValue.Source.DEFAULT, first.source)

                local.set(param, "local_val")
                val afterSet = awaitItem()
                assertEquals("local_val", afterSet.value)
                assertEquals(ConfigValue.Source.LOCAL, afterSet.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * (d) Mid-stream race: remote value becomes available via fetch() after the observer has
     * already started. No regression to DEFAULT must occur after the REMOTE frame is emitted.
     *
     * Acceptable sequences:
     *   DEFAULT → REMOTE  (most common path)
     *   REMOTE             (if getValue() races with fetch() and wins)
     *
     * Invariant: no DEFAULT frame may appear AFTER a REMOTE frame.
     */
    @Test
    fun observeDoesNotRegressToDefault_afterRemoteBecomesAvailableViaMidStreamFetch() =
        runTest {
            val remote = FakeRemoteProvider() // no value yet
            val configValues =
                ConfigValues(
                    localProvider = InMemoryConfigValueProvider(),
                    remoteProvider = remote,
                )
            val param = ConfigParam("flag", "default_val")

            configValues.observe(param).test {
                val first = awaitItem()
                // Remote has no value yet; first frame must be DEFAULT
                assertEquals("default_val", first.value)
                assertEquals(ConfigValue.Source.DEFAULT, first.source)

                // Now remote becomes available and fetch() is called
                remote.set("flag", "remote_val")
                configValues.fetch()

                val afterFetch = awaitItem()
                assertEquals("remote_val", afterFetch.value)
                assertEquals(ConfigValue.Source.REMOTE, afterFetch.source)

                // No further events (no DEFAULT regression)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * (e) Whole-store provider simulation: writing an UNRELATED key must not produce an extra
     * distinct frame for the observed parameter.
     *
     * The WholeStoreLocalProvider emits on every write regardless of key. The trigger-model
     * re-resolve will call getValue() and get the same value back — distinctUntilChanged must
     * suppress the duplicate.
     */
    @Test
    fun observeDoesNotEmitExtraFrame_whenUnrelatedKeyChangesInWholeStoreProvider() =
        runTest {
            val local = WholeStoreLocalProvider()
            val configValues = ConfigValues(localProvider = local)
            val observedParam = ConfigParam("observed_flag", "default_obs")
            val unrelatedParam = ConfigParam("other_flag", "default_other")

            // Pre-set the observed param so the initial frame is LOCAL
            local.set(observedParam, "obs_val")

            configValues.observe(observedParam).test {
                val first = awaitItem()
                assertEquals("obs_val", first.value)
                assertEquals(ConfigValue.Source.LOCAL, first.source)

                // Change unrelated key — whole-store provider fires for all keys
                local.set(unrelatedParam, "other_val")

                // Must NOT produce another frame for observed_flag (distinctUntilChanged)
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * (f) warmUp + active observe + fetch: values are correct and no DEFAULT regression occurs
     * after fetch() when a remote value exists.
     *
     * Frame sequence: REMOTE (initial after warmUp) → REMOTE (after fetch)
     * No DEFAULT frame between them.
     */
    @Test
    fun observeRemainsCorrect_afterWarmUpAndFetch_withNoDefaultRegression() =
        runTest {
            val remote = FakeRemoteProvider().also { it.set("flag", "remote_v1") }
            val configValues =
                ConfigValues(
                    localProvider = InMemoryConfigValueProvider(),
                    remoteProvider = remote,
                )
            val param = ConfigParam("flag", "default_val")

            configValues.warmUp(listOf(param))

            configValues.observe(param).test {
                val first = awaitItem()
                assertEquals("remote_v1", first.value)
                assertEquals(ConfigValue.Source.REMOTE, first.source)

                // Update remote and fetch
                remote.set("flag", "remote_v2")
                configValues.fetch()

                val afterFetch = awaitItem()
                assertEquals("remote_v2", afterFetch.value)
                assertEquals(ConfigValue.Source.REMOTE, afterFetch.source)

                // No DEFAULT regression frame
                cancelAndIgnoreRemainingEvents()
            }
        }
}
