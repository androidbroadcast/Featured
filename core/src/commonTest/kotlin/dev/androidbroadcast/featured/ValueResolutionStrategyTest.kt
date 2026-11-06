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
    private val otherBoolParam = ConfigParam(key = "other_flag", defaultValue = false)
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

    /**
     * Published fail-closed recipe. Selected boolean keys are true only when both provider
     * values are explicitly true. The combined result uses [ConfigValue.Source.UNKNOWN].
     * Other keys delegate to [ValueResolutionStrategy.Default].
     */
    private val andGatedKeys = setOf("flag")

    private val requireBothExplicitlyTrue =
        object : ValueResolutionStrategy {
            override fun <T : Any> resolve(
                param: ConfigParam<T>,
                localValue: ConfigValue<T>?,
                remoteValue: ConfigValue<T>?,
                defaultValue: ConfigValue<T>,
            ): ConfigValue<T> {
                if (defaultValue.value is Boolean && param.key in andGatedKeys) {
                    val enabled = localValue?.value == true && remoteValue?.value == true
                    @Suppress("UNCHECKED_CAST")
                    return ConfigValue(enabled as T, ConfigValue.Source.UNKNOWN)
                }
                return ValueResolutionStrategy.Default.resolve(
                    param,
                    localValue,
                    remoteValue,
                    defaultValue,
                )
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

    // ---------------------------------------------------------------------------
    // Published fail-closed recipe (issue #296): both sides must be explicitly true
    // ---------------------------------------------------------------------------

    @Test
    fun `fail-closed recipe is true with source UNKNOWN when both sides are true`() =
        runTest {
            val configValues = failClosed(local = true, remote = true)

            val result = configValues.getValue(boolParam)
            assertEquals(true, result.value)
            assertEquals(ConfigValue.Source.UNKNOWN, result.source)

            val cached = configValues.getValueCached(boolParam)
            assertEquals(true, cached.value)
            assertEquals(ConfigValue.Source.UNKNOWN, cached.source)
        }

    @Test
    fun `fail-closed recipe is false with source UNKNOWN when remote is false`() =
        runTest {
            val configValues = failClosed(local = true, remote = false)

            val result = configValues.getValue(boolParam)
            assertEquals(false, result.value)
            assertEquals(ConfigValue.Source.UNKNOWN, result.source)
            assertEquals(result, configValues.getValueCached(boolParam))
        }

    @Test
    fun `fail-closed recipe is false with source UNKNOWN when local is false and remote is true`() =
        runTest {
            val configValues = failClosed(local = false, remote = true)

            val result = configValues.getValue(boolParam)
            assertEquals(false, result.value)
            assertEquals(ConfigValue.Source.UNKNOWN, result.source)
            assertEquals(result, configValues.getValueCached(boolParam))
        }

    @Test
    fun `fail-closed recipe is false with source UNKNOWN when local is absent and remote is true`() =
        runTest {
            val configValues = failClosed(local = null, remote = true)

            val result = configValues.getValue(boolParam)
            assertEquals(false, result.value)
            assertEquals(ConfigValue.Source.UNKNOWN, result.source)
            assertEquals(result, configValues.getValueCached(boolParam))
        }

    @Test
    fun `fail-closed recipe is false with source UNKNOWN when local is true and remote is absent`() =
        runTest {
            val configValues = failClosed(local = true, remote = null)

            val result = configValues.getValue(boolParam)
            assertEquals(false, result.value)
            assertEquals(ConfigValue.Source.UNKNOWN, result.source)
            assertEquals(result, configValues.getValueCached(boolParam))
        }

    @Test
    fun `fail-closed recipe writes combined false UNKNOWN when both sides are absent`() =
        runTest {
            val configValues = failClosed(local = null, remote = null)

            val result = configValues.getValue(boolParam)
            assertEquals(false, result.value)
            assertEquals(ConfigValue.Source.UNKNOWN, result.source)

            val cached = configValues.getValueCached(boolParam)
            assertEquals(false, cached.value)
            assertEquals(ConfigValue.Source.UNKNOWN, cached.source)
        }

    @Test
    fun `fail-closed recipe delegates a non-selected string param to Default`() =
        runTest {
            val local = InMemoryConfigValueProvider().apply { set(stringParam, "local_title") }
            val remote = MapRemoteProvider(mapOf("title" to "remote_title", "flag" to true))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothExplicitlyTrue,
                )

            val result = configValues.getValue(stringParam)
            assertEquals("local_title", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun `fail-closed recipe delegates a non-selected boolean with only remote true to Default`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("other_flag" to true))
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothExplicitlyTrue,
                )

            val result = configValues.getValue(otherBoolParam)
            assertEquals(true, result.value)
            assertEquals(ConfigValue.Source.REMOTE, result.source)
        }

    @Test
    fun `Default ConfigValues over the same providers returns remote true when local is absent`() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = MapRemoteProvider(mapOf("flag" to true))
            val andValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = requireBothExplicitlyTrue,
                )
            val defaultValues = ConfigValues(localProvider = local, remoteProvider = remote)

            val andResult = andValues.getValue(boolParam)
            assertEquals(false, andResult.value)
            assertEquals(ConfigValue.Source.UNKNOWN, andResult.source)

            val defaultResult = defaultValues.getValue(boolParam)
            assertEquals(true, defaultResult.value)
            assertEquals(ConfigValue.Source.REMOTE, defaultResult.source)
        }

    private suspend fun failClosed(
        local: Boolean?,
        remote: Boolean?,
    ): ConfigValues {
        val localProvider = InMemoryConfigValueProvider()
        if (local != null) {
            localProvider.set(boolParam, local)
        }
        val remoteValues = if (remote == null) emptyMap() else mapOf("flag" to remote)
        return ConfigValues(
            localProvider = localProvider,
            remoteProvider = MapRemoteProvider(remoteValues),
            resolutionStrategy = requireBothExplicitlyTrue,
        )
    }
}
