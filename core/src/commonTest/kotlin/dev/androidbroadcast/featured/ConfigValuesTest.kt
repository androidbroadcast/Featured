package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigValuesTest {
    // Mock implementation for testing remote provider
    private class MockRemoteProvider : RemoteConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()
        var fetchCalled = false
        var activateOnFetch = false

        fun setMockValue(
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

        override suspend fun fetch(activate: Boolean) {
            fetchCalled = true
            activateOnFetch = activate
        }
    }

    @Test
    fun testObserveEmitsNewValueAfterRemoteFetch() =
        runTest {
            val remoteProvider = MockRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remoteProvider)
            val param = ConfigParam("remote_flag", "default")

            remoteProvider.setMockValue("remote_flag", "initial_remote")

            configValues.observe(param).test {
                // Should emit current remote value first
                val firstEmission = awaitItem()
                assertEquals("initial_remote", firstEmission.value)
                assertEquals(ConfigValue.Source.REMOTE, firstEmission.source)

                // Simulate remote update and fetch
                remoteProvider.setMockValue("remote_flag", "updated_remote")
                configValues.fetch()

                // Should emit new value after fetch
                val secondEmission = awaitItem()
                assertEquals("updated_remote", secondEmission.value)
                assertEquals(ConfigValue.Source.REMOTE, secondEmission.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testObserveDoesNotEmitWhenFetchDoesNotChangeValue() =
        runTest {
            val remoteProvider = MockRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remoteProvider)
            val param = ConfigParam("remote_flag", "default")

            remoteProvider.setMockValue("remote_flag", "same_value")

            configValues.observe(param).test {
                val firstEmission = awaitItem()
                assertEquals("same_value", firstEmission.value)

                // Fetch without changing the remote value — should not emit again
                configValues.fetch()
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testConfigValuesRequiresAtLeastOneProvider() {
        assertFailsWith<IllegalArgumentException> {
            ConfigValues(localProvider = null, remoteProvider = null)
        }
    }

    @Test
    fun testGetValueFromLocalProvider() =
        runTest {
            val localProvider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = localProvider)
            val param = ConfigParam("test_key", "default")

            localProvider.set(param, "local_value")

            val result = configValues.getValue(param)
            assertEquals("local_value", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun testGetValueFromRemoteProvider() =
        runTest {
            val remoteProvider = MockRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remoteProvider)
            val param = ConfigParam("test_key", "default")

            remoteProvider.setMockValue("test_key", "remote_value")

            val result = configValues.getValue(param)
            assertEquals("remote_value", result.value)
            assertEquals(ConfigValue.Source.REMOTE, result.source)
        }

    @Test
    fun testGetValueLocalPriorityOverRemote() =
        runTest {
            val localProvider = InMemoryConfigValueProvider()
            val remoteProvider = MockRemoteProvider()
            val configValues =
                ConfigValues(
                    localProvider = localProvider,
                    remoteProvider = remoteProvider,
                )
            val param = ConfigParam("test_key", "default")

            localProvider.set(param, "local_value")
            remoteProvider.setMockValue("test_key", "remote_value")

            val result = configValues.getValue(param)
            assertEquals("local_value", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun testGetValueFallbackToDefault() =
        runTest {
            val localProvider = InMemoryConfigValueProvider()
            val remoteProvider = MockRemoteProvider()
            val configValues =
                ConfigValues(
                    localProvider = localProvider,
                    remoteProvider = remoteProvider,
                )
            val param = ConfigParam("non_existent", "default_value")

            val result = configValues.getValue(param)
            assertEquals("default_value", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun testOverrideValue() =
        runTest {
            val localProvider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = localProvider)
            val param = ConfigParam("test_key", "default")

            configValues.override(param, "overridden_value")

            val result = configValues.getValue(param)
            assertEquals("overridden_value", result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun testOverrideWithoutLocalProvider() =
        runTest {
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
    fun testFetch() =
        runTest {
            val remoteProvider = MockRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remoteProvider)

            configValues.fetch()

            assertEquals(true, remoteProvider.fetchCalled)
            assertEquals(true, remoteProvider.activateOnFetch)
        }

    @Test
    fun testFetchWithoutRemoteProvider() =
        runTest {
            val localProvider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = localProvider)

            // Should not throw
            configValues.fetch()
        }

    @Test
    fun testObserveValueChanges() =
        runTest {
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
    fun testObserveWithoutLocalProvider() =
        runTest {
            val remoteProvider = MockRemoteProvider()
            val configValues = ConfigValues(remoteProvider = remoteProvider)
            val param = ConfigParam("observe_key", "default")

            remoteProvider.setMockValue("observe_key", "remote_value")

            configValues.observe(param).test {
                // Should emit current value from remote provider
                val emission = awaitItem()
                assertEquals("remote_value", emission.value)
                assertEquals(ConfigValue.Source.REMOTE, emission.source)

                // Flow stays open — future fetch() calls can deliver updated remote values
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testObserveDefaultValue() =
        runTest {
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
    fun testObserveDistinctUntilChanged() =
        runTest {
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

    @Test
    fun clearOverrides_removesAllLocalOverrides_andFallsBackToDefault() =
        runTest {
            val localProvider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = localProvider)
            val param1 = ConfigParam("flag1", "default1")
            val param2 = ConfigParam("flag2", 0)
            configValues.override(param1, "overridden1")
            configValues.override(param2, 42)

            configValues.clearOverrides()

            val result1 = configValues.getValue(param1)
            val result2 = configValues.getValue(param2)
            assertEquals("default1", result1.value)
            assertEquals(ConfigValue.Source.DEFAULT, result1.source)
            assertEquals(0, result2.value)
            assertEquals(ConfigValue.Source.DEFAULT, result2.source)
        }

    @Test
    fun clearOverrides_withNoLocalProvider_doesNotThrow() =
        runTest {
            val configValues = ConfigValues(remoteProvider = MockRemoteProvider())
            val param = ConfigParam("x", 0)

            // Should be a no-op, not throw
            configValues.clearOverrides()

            assertEquals(0, configValues.getValue(param).value)
        }
}
