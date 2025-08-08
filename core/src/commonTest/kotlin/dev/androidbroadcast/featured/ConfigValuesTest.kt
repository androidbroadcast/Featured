package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigValuesTest {

    // Mock implementation for testing remote provider
    private class MockRemoteProvider() : RemoteConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()
        var fetchCalled = false
        var activateOnFetch = false

        fun setMockValue(key: String, value: Any) {
            storage[key] = value
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
            return storage[param.key]?.let { value ->
                ConfigValue(value as T, ConfigValue.Source.REMOTE)
            }
        }

        override suspend fun fetch(activate: Boolean) {
            fetchCalled = true
            activateOnFetch = activate
        }
    }

    @Test
    fun testConfigValuesRequiresAtLeastOneProvider() {
        assertFailsWith<IllegalArgumentException> {
            ConfigValues(localProvider = null, remoteProvider = null)
        }
    }

    @Test
    fun testGetValueFromLocalProvider() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val param = ConfigParam("test_key", "default")

        localProvider.set(param, "local_value")

        val result = configValues.getValue(param)
        assertEquals("local_value", result.value)
        assertEquals(ConfigValue.Source.LOCAL, result.source)
    }

    @Test
    fun testGetValueFromRemoteProvider() = runTest {
        val remoteProvider = MockRemoteProvider()
        val configValues = ConfigValues(remoteProvider = remoteProvider)
        val param = ConfigParam("test_key", "default")

        remoteProvider.setMockValue("test_key", "remote_value")

        val result = configValues.getValue(param)
        assertEquals("remote_value", result.value)
        assertEquals(ConfigValue.Source.REMOTE, result.source)
    }

    @Test
    fun testGetValueLocalPriorityOverRemote() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val remoteProvider = MockRemoteProvider()
        val configValues = ConfigValues(
            localProvider = localProvider,
            remoteProvider = remoteProvider
        )
        val param = ConfigParam("test_key", "default")

        localProvider.set(param, "local_value")
        remoteProvider.setMockValue("test_key", "remote_value")

        val result = configValues.getValue(param)
        assertEquals("local_value", result.value)
        assertEquals(ConfigValue.Source.LOCAL, result.source)
    }

    @Test
    fun testGetValueFallbackToDefault() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val remoteProvider = MockRemoteProvider()
        val configValues = ConfigValues(
            localProvider = localProvider,
            remoteProvider = remoteProvider
        )
        val param = ConfigParam("non_existent", "default_value")

        val result = configValues.getValue(param)
        assertEquals("default_value", result.value)
        assertEquals(ConfigValue.Source.DEFAULT, result.source)
    }

    @Test
    fun testOverrideValue() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val param = ConfigParam("test_key", "default")

        configValues.override(param, "overridden_value")

        val result = configValues.getValue(param)
        assertEquals("overridden_value", result.value)
        assertEquals(ConfigValue.Source.LOCAL, result.source)
    }

    @Test
    fun testOverrideWithoutLocalProvider() = runTest {
        val remoteProvider = MockRemoteProvider()
        val configValues = ConfigValues(remoteProvider = remoteProvider)
        val param = ConfigParam("test_key", "default")

        // Should not throw, but also shouldn't do anything
        configValues.override(param, "value")

        val result = configValues.getValue(param)
        assertEquals("default", result.value)
        assertEquals(ConfigValue.Source.DEFAULT, result.source)
    }

    @Test
    fun testFetch() = runTest {
        val remoteProvider = MockRemoteProvider()
        val configValues = ConfigValues(remoteProvider = remoteProvider)

        configValues.fetch()

        assertEquals(true, remoteProvider.fetchCalled)
        assertEquals(true, remoteProvider.activateOnFetch)
    }

    @Test
    fun testFetchWithoutRemoteProvider() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)

        // Should not throw
        configValues.fetch()
    }

    @Test
    fun testObserveValueChanges() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val param = ConfigParam("observe_key", "default")

        localProvider.set(param, "initial")

        configValues.observe(param).test {
            // Should emit current value first
            val firstEmission = awaitItem()
            assertEquals("initial", firstEmission.value)
            assertEquals(ConfigValue.Source.LOCAL, firstEmission.source)

            // Update value
            localProvider.set(param, "updated")
            val secondEmission = awaitItem()
            assertEquals("updated", secondEmission.value)
            assertEquals(ConfigValue.Source.LOCAL, secondEmission.source)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testObserveWithoutLocalProvider() = runTest {
        val remoteProvider = MockRemoteProvider()
        val configValues = ConfigValues(remoteProvider = remoteProvider)
        val param = ConfigParam("observe_key", "default")

        remoteProvider.setMockValue("observe_key", "remote_value")

        configValues.observe(param).test {
            // Should emit current value from remote provider
            val emission = awaitItem()
            assertEquals("remote_value", emission.value)
            assertEquals(ConfigValue.Source.REMOTE, emission.source)

            // Flow will complete since there's no local provider to observe changes
            awaitComplete()
        }
    }

    @Test
    fun testObserveDefaultValue() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val param = ConfigParam("non_existent", "default_value")

        configValues.observe(param).test {
            val emission = awaitItem()
            assertEquals("default_value", emission.value)
            assertEquals(ConfigValue.Source.DEFAULT, emission.source)

            // Set a value and observe the change
            localProvider.set(param, "new_value")
            val newEmission = awaitItem()
            assertEquals("new_value", newEmission.value)
            assertEquals(ConfigValue.Source.LOCAL, newEmission.source)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testObserveDistinctUntilChanged() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val param = ConfigParam("distinct_key", "default")

        localProvider.set(param, "value")

        configValues.observe(param).test {
            // Initial emission
            val firstEmission = awaitItem()
            assertEquals("value", firstEmission.value)

            // Set same value - should not emit
            localProvider.set(param, "value")
            expectNoEvents()

            // Set different value - should emit
            localProvider.set(param, "new_value")
            val secondEmission = awaitItem()
            assertEquals("new_value", secondEmission.value)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
