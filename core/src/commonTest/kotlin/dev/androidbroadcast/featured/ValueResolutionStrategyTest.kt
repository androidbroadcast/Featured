package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Unit tests for [ValueResolutionStrategy] — the pluggable value-selection policy — and its
 * wiring inside [ConfigValues.getValue], [ConfigValues.override], [ConfigValues.resetOverride],
 * and [ConfigValues.observe].
 */
class ValueResolutionStrategyTest {
    private val boolParam = ConfigParam(key = "flag", defaultValue = false)
    private val stringParam = ConfigParam(key = "title", defaultValue = "default_title")

    /** Map-backed remote provider that counts [get] invocations. */
    private class MapRemoteProvider(
        private val values: Map<String, Any> = emptyMap(),
    ) : RemoteConfigValueProvider {
        var getCalls = 0
            private set

        override suspend fun fetch(activate: Boolean) = Unit

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
            getCalls++
            return values[param.key]?.let { ConfigValue(it as T, ConfigValue.Source.REMOTE) }
        }
    }

    private class ThrowingLocalProvider : LocalConfigValueProvider {
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = throw RuntimeException("local failure")

        override suspend fun <T : Any> set(
            param: ConfigParam<T>,
            value: T,
        ) = Unit

        override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) = Unit

        override suspend fun clear() = Unit

        override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> = emptyFlow()
    }

    /**
     * "Kill-switch AND": when both a local and a remote Boolean value are present, the flag is
     * enabled only when they are both true; otherwise the classic fallback chain applies.
     */
    private val requireBothTrue =
        object : ValueResolutionStrategy {
            override fun <T : Any> resolve(
                param: ConfigParam<T>,
                localValue: ConfigValue<T>?,
                remoteValue: ConfigValue<T>?,
                defaultValue: ConfigValue<T>,
            ): ConfigValue<T> {
                val local = localValue?.value
                val remote = remoteValue?.value
                if (local is Boolean && remote is Boolean) {
                    @Suppress("UNCHECKED_CAST")
                    return ConfigValue((local && remote) as T, ConfigValue.Source.UNKNOWN)
                }
                return localValue ?: remoteValue ?: defaultValue
            }
        }

    private val remoteOnly =
        object : ValueResolutionStrategy {
            override fun <T : Any> resolve(
                param: ConfigParam<T>,
                localValue: ConfigValue<T>?,
                remoteValue: ConfigValue<T>?,
                defaultValue: ConfigValue<T>,
            ): ConfigValue<T> = remoteValue ?: defaultValue
        }

    // ---------------------------------------------------------------------------
    // Default strategy — behavior identical to the historical hardcoded chain
    // ---------------------------------------------------------------------------

    @Test
    fun `Default strategy prefers local over remote`() =
        runTest {
            val local = InMemoryConfigValueProvider().apply { set(stringParam, "local_title") }
            val remote = MapRemoteProvider(mapOf("title" to "remote_title"))
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)

            val result = configValues.getValue(stringParam)

            assertEquals("local_title", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `Default strategy falls back to remote then default and does not cache the default`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("title" to "remote_title"))
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)

            val fromRemote = configValues.getValue(stringParam)
            assertEquals("remote_title", fromRemote.value)
            assertEquals(ConfigValue.Source.REMOTE, fromRemote.source)

            val fromDefault = configValues.getValue(boolParam)
            assertEquals(false, fromDefault.value)
            assertEquals(ConfigValue.Source.DEFAULT, fromDefault.source)
            // Pure default is not written through to the snapshot.
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(boolParam).source)
        }

    @Test
    fun `remote provider is queried even when local has a value`() =
        runTest {
            val local = InMemoryConfigValueProvider().apply { set(stringParam, "local_title") }
            val remote = MapRemoteProvider(mapOf("title" to "remote_title"))
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)

            configValues.getValue(stringParam)

            assertEquals(1, remote.getCalls)
        }

    // ---------------------------------------------------------------------------
    // Custom strategy — invocation contract
    // ---------------------------------------------------------------------------

    @Test
    fun `custom strategy receives param and all resolved candidates`() =
        runTest {
            var capturedParam: ConfigParam<*>? = null
            var capturedLocal: ConfigValue<*>? = null
            var capturedRemote: ConfigValue<*>? = null
            var capturedDefault: ConfigValue<*>? = null
            val capturing =
                object : ValueResolutionStrategy {
                    override fun <T : Any> resolve(
                        param: ConfigParam<T>,
                        localValue: ConfigValue<T>?,
                        remoteValue: ConfigValue<T>?,
                        defaultValue: ConfigValue<T>,
                    ): ConfigValue<T> {
                        capturedParam = param
                        capturedLocal = localValue
                        capturedRemote = remoteValue
                        capturedDefault = defaultValue
                        return localValue ?: remoteValue ?: defaultValue
                    }
                }
            val local = InMemoryConfigValueProvider().apply { set(stringParam, "local_title") }
            val remote = MapRemoteProvider(mapOf("title" to "remote_title"))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = capturing,
                )

            configValues.getValue(stringParam)

            assertSame(stringParam, capturedParam)
            assertEquals(ConfigValue("local_title", ConfigValue.Source.LOCAL), capturedLocal)
            assertEquals(ConfigValue("remote_title", ConfigValue.Source.REMOTE), capturedRemote)
            assertEquals(ConfigValue("default_title", ConfigValue.Source.DEFAULT), capturedDefault)
        }

    @Test
    fun `strategy returning the default candidate does not warm the snapshot`() =
        runTest {
            val alwaysDefault =
                object : ValueResolutionStrategy {
                    override fun <T : Any> resolve(
                        param: ConfigParam<T>,
                        localValue: ConfigValue<T>?,
                        remoteValue: ConfigValue<T>?,
                        defaultValue: ConfigValue<T>,
                    ): ConfigValue<T> = defaultValue
                }
            val local = InMemoryConfigValueProvider().apply { set(stringParam, "local_title") }
            val configValues = ConfigValues(localProvider = local, resolutionStrategy = alwaysDefault)

            val result = configValues.getValue(stringParam)

            assertEquals("default_title", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(stringParam).source)
        }

    @Test
    fun `failed provider yields null to the strategy and reports the error`() =
        runTest {
            val capturedErrors = mutableListOf<Throwable>()
            var localSeenByStrategy: ConfigValue<*>? = ConfigValue("sentinel", ConfigValue.Source.UNKNOWN)
            var remoteSeenByStrategy: ConfigValue<*>? = null
            val capturing =
                object : ValueResolutionStrategy {
                    override fun <T : Any> resolve(
                        param: ConfigParam<T>,
                        localValue: ConfigValue<T>?,
                        remoteValue: ConfigValue<T>?,
                        defaultValue: ConfigValue<T>,
                    ): ConfigValue<T> {
                        localSeenByStrategy = localValue
                        remoteSeenByStrategy = remoteValue
                        return localValue ?: remoteValue ?: defaultValue
                    }
                }
            val configValues =
                ConfigValues(
                    localProvider = ThrowingLocalProvider(),
                    remoteProvider = MapRemoteProvider(mapOf("title" to "remote_title")),
                    onProviderError = { capturedErrors += it },
                    resolutionStrategy = capturing,
                )

            val result = configValues.getValue(stringParam)

            assertNull(localSeenByStrategy)
            assertNotNull(remoteSeenByStrategy)
            assertEquals("remote_title", result.value)
            assertEquals(1, capturedErrors.size)
            assertEquals("local failure", capturedErrors[0].message)
        }

    // ---------------------------------------------------------------------------
    // Remote-only policy (issue #276: ignore local overrides for selected setups)
    // ---------------------------------------------------------------------------

    @Test
    fun `remote-only strategy ignores local override in getValue and getValueCached`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("title" to "remote_title"))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = remoteOnly,
                )

            configValues.override(stringParam, "local_title")

            val resolved = configValues.getValue(stringParam)
            assertEquals("remote_title", resolved.value)
            assertEquals(ConfigValue.Source.REMOTE, resolved.source)

            // override() writes the strategy result through, not the raw LOCAL value.
            val cached = configValues.getValueCached(stringParam)
            assertEquals("remote_title", cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // Kill-switch AND policy (issue #276: local AND remote must both be true)
    // ---------------------------------------------------------------------------

    @Test
    fun `require-both-true strategy disables the flag when remote is false`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("flag" to false))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothTrue,
                )

            configValues.override(boolParam, true)

            assertEquals(false, configValues.getValue(boolParam).value)
            assertEquals(false, configValues.getValueCached(boolParam).value)
        }

    @Test
    fun `require-both-true strategy enables the flag when local and remote are both true`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("flag" to true))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothTrue,
                )

            configValues.override(boolParam, true)

            assertEquals(true, configValues.getValue(boolParam).value)
            assertEquals(true, configValues.getValueCached(boolParam).value)
        }

    // ---------------------------------------------------------------------------
    // resetOverride convergence
    // ---------------------------------------------------------------------------

    @Test
    fun `resetOverride with custom strategy converges the snapshot to the re-resolved value`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("flag" to true))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothTrue,
                )

            configValues.override(boolParam, false)
            assertEquals(false, configValues.getValueCached(boolParam).value)

            configValues.resetOverride(boolParam)

            // Local gone → strategy falls back to localValue ?: remoteValue ?: defaultValue.
            val cached = configValues.getValueCached(boolParam)
            assertEquals(true, cached.value)
            assertEquals(ConfigValue.Source.REMOTE, cached.source)
        }

    // ---------------------------------------------------------------------------
    // observe() re-resolves through the strategy
    // ---------------------------------------------------------------------------

    @Test
    fun `observe re-resolves emissions through the custom strategy on local change`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("flag" to true))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothTrue,
                )

            configValues.observe(boolParam).test {
                // No local value yet → fallback chain → remote wins.
                val initial = awaitItem()
                assertEquals(true, initial.value)
                assertEquals(ConfigValue.Source.REMOTE, initial.source)

                // Local kill-switch flips to false → AND-combined result must be false.
                configValues.override(boolParam, false)
                val combined = awaitItem()
                assertEquals(false, combined.value)
                assertEquals(ConfigValue.Source.UNKNOWN, combined.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------------------------------------------------------------------------
    // Default object direct contract
    // ---------------------------------------------------------------------------

    @Test
    fun `Default object applies local-remote-default chain directly`() {
        val localValue = ConfigValue("local", ConfigValue.Source.LOCAL)
        val remoteValue = ConfigValue("remote", ConfigValue.Source.REMOTE)
        val defaultValue = ConfigValue("default", ConfigValue.Source.DEFAULT)

        assertSame(localValue, ValueResolutionStrategy.Default.resolve(stringParam, localValue, remoteValue, defaultValue))
        assertSame(remoteValue, ValueResolutionStrategy.Default.resolve(stringParam, null, remoteValue, defaultValue))
        assertSame(defaultValue, ValueResolutionStrategy.Default.resolve(stringParam, null, null, defaultValue))
    }
}
