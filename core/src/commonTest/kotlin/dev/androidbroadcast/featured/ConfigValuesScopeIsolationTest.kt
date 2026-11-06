package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigValuesScopeIsolationTest {
    @Test
    fun localAndRemoteFeatureXDoNotShareSnapshot() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = RecordingRemote()
            remote.values["FEATURE_X"] = "remote-1"
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)
            val localFlag = ConfigParam("FEATURE_X", "L", FlagScope.LOCAL)
            val remoteFlag = ConfigParam("FEATURE_X", "R", FlagScope.REMOTE)

            assertEquals("L", configValues.getValueCached(localFlag).value)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(localFlag).source)
            assertEquals("R", configValues.getValueCached(remoteFlag).value)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(remoteFlag).source)

            configValues.override(localFlag, "local-override")

            assertEquals("local-override", configValues.getValueCached(localFlag).value)
            assertEquals(ConfigValue.Source.LOCAL, configValues.getValueCached(localFlag).source)
            assertEquals("R", configValues.getValueCached(remoteFlag).value)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(remoteFlag).source)

            val localResolved = configValues.getValue(localFlag)
            val remoteResolved = configValues.getValue(remoteFlag)
            assertEquals("local-override", localResolved.value)
            assertEquals(ConfigValue.Source.LOCAL, localResolved.source)
            assertEquals("remote-1", remoteResolved.value)
            assertEquals(ConfigValue.Source.REMOTE, remoteResolved.source)
            assertEquals("local-override", configValues.getValueCached(localFlag).value)
            assertEquals("remote-1", configValues.getValueCached(remoteFlag).value)

            configValues.warmUp(listOf(localFlag, remoteFlag))
            remote.values["FEATURE_X"] = "remote-2"
            configValues.fetch()

            assertEquals("local-override", configValues.getValue(localFlag).value)
            assertEquals("remote-2", configValues.getValue(remoteFlag).value)
            assertEquals("local-override", configValues.getValueCached(localFlag).value)
            assertEquals("remote-2", configValues.getValueCached(remoteFlag).value)
            assertEquals(ConfigValue.Source.LOCAL, configValues.getValueCached(localFlag).source)
            assertEquals(ConfigValue.Source.REMOTE, configValues.getValueCached(remoteFlag).source)
        }

    @Test
    fun sameScopeAndKeyIsLastWriteWinsInWarmSet() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = RecordingRemote()
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)
            val first = ConfigParam("FEATURE_X", "first", FlagScope.LOCAL)
            val second = ConfigParam("FEATURE_X", "second", FlagScope.LOCAL)
            val remoteFlag = ConfigParam("FEATURE_X", "R", FlagScope.REMOTE)

            local.set(first, "from-first")
            configValues.warmUp(listOf(first, second))
            assertEquals("from-first", configValues.getValueCached(first).value)
            assertEquals("from-first", configValues.getValueCached(second).value)

            local.set(second, "from-second")
            configValues.fetch()
            assertEquals("from-second", configValues.getValueCached(first).value)
            assertEquals("from-second", configValues.getValueCached(second).value)
            assertEquals("R", configValues.getValueCached(remoteFlag).value)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValueCached(remoteFlag).source)
        }

    @Test
    fun remoteValueDoesNotLeakIntoLocalFlag() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = RecordingRemote(throwOnGet = true)
            val strategy = RecordingStrategy()
            val errors = mutableListOf<Throwable>()
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    onProviderError = { errors += it },
                    resolutionStrategy = strategy,
                )
            val localFlag = ConfigParam("FEATURE_X", "L", FlagScope.LOCAL)
            remote.values["FEATURE_X"] = "remote-1"
            configValues.override(localFlag, "local-override")
            assertTrue(remote.requestedKeys.isEmpty(), "LOCAL override must not call remote")
            assertTrue(errors.isEmpty(), "LOCAL override must not report a skipped remote")

            strategy.clear()
            remote.requestedKeys.clear()
            errors.clear()
            val resolved = configValues.getValue(localFlag)

            assertEquals("local-override", resolved.value)
            assertEquals(ConfigValue.Source.LOCAL, resolved.source)
            assertTrue(remote.requestedKeys.isEmpty(), "remote get must not be called for a LOCAL read")
            assertEquals(1, strategy.calls)
            assertNull(strategy.lastRemote)
            assertTrue(errors.isEmpty(), "a skipped remote get must not call onProviderError")
        }

    @Test
    fun localOverrideDoesNotLeakIntoRemoteFlag() =
        runTest {
            val local = RecordingLocal()
            val remote = RecordingRemote()
            val strategy = RecordingStrategy()
            val configValues =
                ConfigValues(
                    localProvider = local,
                    remoteProvider = remote,
                    resolutionStrategy = strategy,
                )
            val localFlag = ConfigParam("FEATURE_X", "L", FlagScope.LOCAL)
            val remoteFlag = ConfigParam("FEATURE_X", "R", FlagScope.REMOTE)
            remote.values["FEATURE_X"] = "from-remote"
            configValues.override(localFlag, "local-override")

            local.getCalls.clear()
            strategy.clear()
            val resolved = configValues.getValue(remoteFlag)
            assertEquals("from-remote", resolved.value)
            assertEquals(ConfigValue.Source.REMOTE, resolved.source)
            assertTrue(local.getCalls.isEmpty(), "REMOTE getValue must not call local get")
            assertEquals(1, strategy.calls)
            assertNull(strategy.lastLocal)
            assertEquals("local-override", configValues.getValue(localFlag).value)

            local.getCalls.clear()
            strategy.clear()
            configValues.override(remoteFlag, "should-not-win")
            assertEquals(1, strategy.calls)
            assertNull(strategy.lastLocal)
            assertTrue(local.getCalls.isEmpty(), "REMOTE override resolution must not call local get")
            assertEquals("from-remote", configValues.getValue(remoteFlag).value)
            assertEquals(ConfigValue.Source.REMOTE, configValues.getValueCached(remoteFlag).source)

            remote.values.clear()
            assertEquals("R", configValues.getValue(remoteFlag).value)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValue(remoteFlag).source)

            remote.values["FEATURE_X"] = "from-remote"
            local.getCalls.clear()
            strategy.clear()
            configValues.resetOverride(remoteFlag)
            assertEquals(1, strategy.calls)
            assertNull(strategy.lastLocal)
            assertTrue(local.getCalls.isEmpty(), "REMOTE resetOverride resolution must not call local get")
            assertEquals("from-remote", configValues.getValue(remoteFlag).value)
            assertEquals("local-override", configValues.getValue(localFlag).value)
        }

    @Test
    fun anyScopeKeepsLocalThenRemoteThenDefault() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = RecordingRemote()
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)
            val any = ConfigParam("FEATURE_X", "D")

            assertEquals(FlagScope.ANY, any.scope)
            assertEquals("D", configValues.getValue(any).value)
            assertEquals(ConfigValue.Source.DEFAULT, configValues.getValue(any).source)

            remote.values["FEATURE_X"] = "remote-only"
            val remoteOnly = configValues.getValue(any)
            assertEquals("remote-only", remoteOnly.value)
            assertEquals(ConfigValue.Source.REMOTE, remoteOnly.source)

            remote.values.clear()
            configValues.override(any, "local-only")
            val localOnly = configValues.getValue(any)
            assertEquals("local-only", localOnly.value)
            assertEquals(ConfigValue.Source.LOCAL, localOnly.source)

            remote.values["FEATURE_X"] = "remote-too"
            val both = configValues.getValue(any)
            assertEquals("local-only", both.value)
            assertEquals(ConfigValue.Source.LOCAL, both.source)
            assertEquals("ANY:FEATURE_X", any.localStorageKey())
        }

    @Test
    fun remoteProviderIsAskedForThePublicKey() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val remote = RecordingRemote()
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)
            val localFlag = ConfigParam("FEATURE_X", "L", FlagScope.LOCAL)
            val remoteFlag = ConfigParam("FEATURE_X", "R", FlagScope.REMOTE)
            val any = ConfigParam("FEATURE_X", "D")
            remote.values["FEATURE_X"] = "remote-1"

            configValues.getValue(localFlag)
            configValues.getValue(remoteFlag)
            configValues.getValue(any)

            assertEquals(listOf("FEATURE_X", "FEATURE_X"), remote.requestedKeys)
            assertTrue(remote.requestedKeys.none { it == "REMOTE:FEATURE_X" || it == "ANY:FEATURE_X" || it == "LOCAL:FEATURE_X" })
        }

    private class RecordingRemote(
        private val throwOnGet: Boolean = false,
    ) : RemoteConfigValueProvider {
        val values = mutableMapOf<String, Any>()
        val requestedKeys = mutableListOf<String>()

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
            requestedKeys += param.key
            if (throwOnGet) error("remote boom")
            val stored = values[param.key] ?: return null
            return ConfigValue(stored as T, ConfigValue.Source.REMOTE)
        }

        override suspend fun fetch(activate: Boolean) = Unit
    }

    private class RecordingLocal : LocalConfigValueProvider {
        val getCalls = mutableListOf<String>()
        private val storage = mutableMapOf<String, Any>()

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
            getCalls += param.key
            val stored = storage[param.localStorageKey()] ?: return null
            return ConfigValue(stored as T, ConfigValue.Source.LOCAL)
        }

        override suspend fun <T : Any> set(
            param: ConfigParam<T>,
            value: T,
        ) {
            storage[param.localStorageKey()] = value
        }

        override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
            storage.remove(param.localStorageKey())
        }

        override suspend fun clear() {
            storage.clear()
        }

        override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> = emptyFlow()
    }

    private class RecordingStrategy : ValueResolutionStrategy {
        var calls: Int = 0
        var lastLocal: ConfigValue<*>? = null
        var lastRemote: ConfigValue<*>? = null

        fun clear() {
            calls = 0
            lastLocal = null
            lastRemote = null
        }

        override fun <T : Any> resolve(
            param: ConfigParam<T>,
            localValue: ConfigValue<T>?,
            remoteValue: ConfigValue<T>?,
            defaultValue: ConfigValue<T>,
        ): ConfigValue<T> {
            calls += 1
            lastLocal = localValue
            lastRemote = remoteValue
            return localValue ?: remoteValue ?: defaultValue
        }
    }
}
