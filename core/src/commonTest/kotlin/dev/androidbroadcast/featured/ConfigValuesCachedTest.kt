package dev.androidbroadcast.featured

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Unit tests for [ConfigValues.getValueCached] — the synchronous read path — and its
 * write-through wiring from [ConfigValues.getValue], [ConfigValues.override],
 * [ConfigValues.resetOverride], and [ConfigValues.isEnabled].
 */
class ConfigValuesCachedTest {
    private val param = ConfigParam(key = "flag", defaultValue = false)

    // ---------------------------------------------------------------------------
    // Cold-read before any warm-up
    // ---------------------------------------------------------------------------

    @Test
    fun `getValueCached returns DEFAULT before any warm-up`() {
        val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())

        val result = configValues.getValueCached(param)

        assertEquals(false, result.value)
        assertEquals(ConfigValue.Source.DEFAULT, result.source)
    }

    // ---------------------------------------------------------------------------
    // Write-through from override
    // ---------------------------------------------------------------------------

    @Test
    fun `getValueCached returns LOCAL after override`() =
        runTest {
            val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())
            configValues.override(param, true)

            val result = configValues.getValueCached(param)

            assertEquals(true, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    // ---------------------------------------------------------------------------
    // Write-through from suspend getValue
    // ---------------------------------------------------------------------------

    @Test
    fun `getValueCached after suspend getValue reflects the resolved value`() =
        runTest {
            val remote =
                object : RemoteConfigValueProvider {
                    override suspend fun fetch(activate: Boolean) = Unit

                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
                        @Suppress("UNCHECKED_CAST")
                        return if (param.key == "flag") ConfigValue(true as T, ConfigValue.Source.REMOTE) else null
                    }
                }
            val configValues = ConfigValues(remoteProvider = remote)

            // Snapshot is empty before getValue
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(param).source)

            configValues.getValue(param)

            // Snapshot is now warm
            val cached = configValues.getValueCached(param)
            assertEquals(true, cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // Write-through from remote fetch path (via observe / getValue after fetch)
    // ---------------------------------------------------------------------------

    @Test
    fun `getValueCached returns REMOTE after fetch and getValue`() =
        runTest {
            val remote =
                object : RemoteConfigValueProvider {
                    override suspend fun fetch(activate: Boolean) = Unit

                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
                        @Suppress("UNCHECKED_CAST")
                        return ConfigValue(true as T, ConfigValue.Source.REMOTE)
                    }
                }
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.fetch()
            configValues.getValue(param) // warms the snapshot

            val cached = configValues.getValueCached(param)
            assertEquals(true, cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // resetOverride re-resolution
    // ---------------------------------------------------------------------------

    @Test
    fun `getValueCached after resetOverride re-resolves through priority chain`() =
        runTest {
            val remote =
                object : RemoteConfigValueProvider {
                    override suspend fun fetch(activate: Boolean) = Unit

                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
                        @Suppress("UNCHECKED_CAST")
                        return if (param.key == "flag") ConfigValue(true as T, ConfigValue.Source.REMOTE) else null
                    }
                }
            val local = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)

            // Set a local override
            configValues.override(param, false)
            assertEquals(false, configValues.getValueCached(param).value)
            assertEquals(ConfigValue.Source.LOCAL, configValues.getValueCached(param).source)

            // Reset the override; the local provider no longer holds a value.
            configValues.resetOverride(param)

            // Re-resolve via the suspend path — this is the observable contract: after
            // resetOverride, getValue re-applies the priority chain (remote wins here).
            val resolved = configValues.getValue(param)
            assertEquals(true, resolved.value)
            assertEquals(ConfigValue.Source.REMOTE, resolved.source)

            // The write-through from getValue means getValueCached now reflects REMOTE too.
            val cached = configValues.getValueCached(param)
            assertEquals(true, cached.value)
            assertNotEquals(ConfigValue.Source.LOCAL, cached.source)
        }

    // ---------------------------------------------------------------------------
    // Thread-safety: concurrent reads and writes
    // ---------------------------------------------------------------------------

    @Test
    fun `getValueCached is thread-safe under concurrent reads and writes`() =
        runTest {
            val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())

            // Launch 100 concurrent override writes with alternating values
            repeat(100) { i ->
                launch {
                    configValues.override(param, i % 2 == 0)
                }
            }

            // Perform 100 concurrent reads while writes are in flight — must not throw.
            // No value assertion here: a Boolean is always true or false; the invariant under
            // test is absence of exceptions and absence of data races on the AtomicReference.
            repeat(100) {
                configValues.getValueCached(param)
            }

            testScheduler.runCurrent()

            // After all coroutines complete, the snapshot must hold one of the written values
            // (not an illegal state). Source must be LOCAL because override() was called.
            assertEquals(ConfigValue.Source.LOCAL, configValues.getValueCached(param).source)
        }

    // ---------------------------------------------------------------------------
    // Duplicate-key last-write-wins semantic
    // ---------------------------------------------------------------------------

    @Test
    fun `last-write-wins - two ConfigParams with same key share snapshot slot`() =
        runTest {
            val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())
            val param1 = ConfigParam(key = "shared_key", defaultValue = false)
            val param2 = ConfigParam(key = "shared_key", defaultValue = false)

            configValues.override(param1, false)
            configValues.override(param2, true) // wins because it is the last write

            // Both params read from the same snapshot slot
            assertEquals(true, configValues.getValueCached(param1).value)
            assertEquals(true, configValues.getValueCached(param2).value)
        }

    // ---------------------------------------------------------------------------
    // Sync isEnabled
    // ---------------------------------------------------------------------------

    @Test
    fun `sync isEnabled returns false before warm-up for default=false param`() {
        val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())

        assertEquals(false, configValues.isEnabled(param))
    }

    @Test
    fun `sync isEnabled returns true after override with true value`() =
        runTest {
            val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())
            configValues.override(param, true)

            assertEquals(true, configValues.isEnabled(param))
        }
}
