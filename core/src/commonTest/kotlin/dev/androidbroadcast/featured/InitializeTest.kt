package dev.androidbroadcast.featured

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitializeTest {
    /**
     * A remote provider that simulates having cached values available locally
     * (e.g. Firebase Remote Config with a disk cache). Tracks whether initialize()
     * was called, and whether fetch() was called.
     */
    private class CachingRemoteProvider(
        private val cachedValues: Map<String, Any> = emptyMap(),
    ) : RemoteConfigValueProvider,
        InitializableConfigValueProvider {
        var initializeCalled = false
        var fetchCalled = false

        private val activeValues = mutableMapOf<String, Any>()

        override suspend fun initialize() {
            initializeCalled = true
            activeValues.putAll(cachedValues)
        }

        override suspend fun fetch(activate: Boolean) {
            fetchCalled = true
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
            activeValues[param.key]?.let { value ->
                ConfigValue(value as T, ConfigValue.Source.REMOTE)
            }
    }

    /** A remote provider that does not implement InitializableConfigValueProvider. */
    private class PlainRemoteProvider : RemoteConfigValueProvider {
        var fetchCalled = false

        override suspend fun fetch(activate: Boolean) {
            fetchCalled = true
        }

        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = null
    }

    @Test
    fun initializeLoadsRemoteCacheIntoMemory() =
        runTest {
            val param = ConfigParam("flag", "default")
            val remote = CachingRemoteProvider(cachedValues = mapOf("flag" to "cached_remote"))
            val configValues = ConfigValues(remoteProvider = remote)

            val beforeInit = configValues.getValue(param)
            assertEquals("default", beforeInit.value)
            assertEquals(ConfigValue.Source.DEFAULT, beforeInit.source)

            configValues.initialize()

            val afterInit = configValues.getValue(param)
            assertEquals("cached_remote", afterInit.value)
            assertEquals(ConfigValue.Source.REMOTE, afterInit.source)
        }

    @Test
    fun initializeCallsInitializeOnRemoteProviderWhenSupported() =
        runTest {
            val remote = CachingRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.initialize()

            assertTrue(remote.initializeCalled)
        }

    @Test
    fun initializeDoesNotFetchFromRemote() =
        runTest {
            val remote = CachingRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.initialize()

            assertFalse(remote.fetchCalled)
        }

    @Test
    fun initializeIsNoOpForPlainRemoteProvider() =
        runTest {
            val remote = PlainRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remote)

            configValues.initialize()

            assertFalse(remote.fetchCalled)
        }

    @Test
    fun initializeIsNoOpWhenOnlyLocalProvider() =
        runTest {
            val local = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = local)
            val param = ConfigParam("flag", "default")

            local.set(param, "local_value")

            configValues.initialize()

            val result = configValues.getValue(param)
            assertEquals("local_value", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun localProviderTakesPriorityOverInitializedRemoteCache() =
        runTest {
            val param = ConfigParam("flag", "default")
            val local = InMemoryConfigValueProvider()
            val remote = CachingRemoteProvider(cachedValues = mapOf("flag" to "cached_remote"))
            val configValues = ConfigValues(localProvider = local, remoteProvider = remote)

            local.set(param, "local_override")
            configValues.initialize()

            val result = configValues.getValue(param)
            assertEquals("local_override", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }
}
