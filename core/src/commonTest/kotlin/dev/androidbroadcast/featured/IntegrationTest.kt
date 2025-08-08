package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationTest {

    @Test
    fun testFullWorkflowWithBothProviders() = runTest {
        // Setup providers
        val localProvider = InMemoryConfigValueProvider()
        val remoteProvider = TestRemoteProvider()
        val configValues = ConfigValues(localProvider, remoteProvider)

        // Register some parameters
        val stringParam = ConfigParamRegistry.register(
            ConfigParam("app.feature.enabled", "default_string")
        )
        val intParam = ConfigParamRegistry.register(
            ConfigParam("app.max.retries", 3)
        )
        val boolParam = ConfigParamRegistry.register(
            ConfigParam("app.debug.mode", false)
        )

        // Test 1: Default values when no providers have values
        assertEquals("default_string", configValues.getValue(stringParam).value)
        assertEquals(ConfigValue.Source.DEFAULT, configValues.getValue(stringParam).source)

        assertEquals(3, configValues.getValue(intParam).value)
        assertEquals(ConfigValue.Source.DEFAULT, configValues.getValue(intParam).source)

        // Test 2: Remote values override defaults
        remoteProvider.setMockValue("app.feature.enabled", "remote_string")
        remoteProvider.setMockValue("app.max.retries", 5)

        assertEquals("remote_string", configValues.getValue(stringParam).value)
        assertEquals(ConfigValue.Source.REMOTE, configValues.getValue(stringParam).source)

        assertEquals(5, configValues.getValue(intParam).value)
        assertEquals(ConfigValue.Source.REMOTE, configValues.getValue(intParam).source)

        // Test 3: Local values override remote values
        configValues.override(stringParam, "local_override")
        configValues.override(boolParam, true)

        assertEquals("local_override", configValues.getValue(stringParam).value)
        assertEquals(ConfigValue.Source.LOCAL, configValues.getValue(stringParam).source)

        assertEquals(true, configValues.getValue(boolParam).value)
        assertEquals(ConfigValue.Source.LOCAL, configValues.getValue(boolParam).source)

        // Remote value should still be accessible for non-overridden params
        assertEquals(5, configValues.getValue(intParam).value)
        assertEquals(ConfigValue.Source.REMOTE, configValues.getValue(intParam).source)

        // Test 4: Fetch from remote provider
        configValues.fetch()
        assertTrue(remoteProvider.fetchCalled)

        // Cleanup
        ConfigParamRegistry.clear()
    }

    @Test
    fun testParameterRegistryWithConfigValues() = runTest {
        ConfigParamRegistry.clear()

        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)

        // Register parameters
        val param1 = ConfigParamRegistry.register(ConfigParam("param1", "value1"))
        val param2 = ConfigParamRegistry.register(ConfigParam("param2", 42))
        val param3 = ConfigParamRegistry.register(ConfigParam("param3", true))

        // Verify registry contains all parameters
        val allParams = ConfigParamRegistry.getAll()
        assertEquals(3, allParams.size)
        assertTrue(allParams.contains(param1))
        assertTrue(allParams.contains(param2))
        assertTrue(allParams.contains(param3))

        // Test that we can get values for all registered parameters
        assertEquals("value1", configValues.getValue(param1).value)
        assertEquals(42, configValues.getValue(param2).value)
        assertEquals(true, configValues.getValue(param3).value)

        // Override some values
        configValues.override(param1, "overridden1")
        configValues.override(param2, 100)

        assertEquals("overridden1", configValues.getValue(param1).value)
        assertEquals(100, configValues.getValue(param2).value)
        assertEquals(true, configValues.getValue(param3).value) // unchanged

        ConfigParamRegistry.clear()
    }

    @Test
    fun testObserveWithMultipleUpdates() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val param = ConfigParam("observe_test", "initial")

        // Set initial value
        localProvider.set(param, "step1")

        configValues.observe(param).test {
            // Should emit initial value
            assertEquals("step1", awaitItem().value)

            // Update through ConfigValues.override
            configValues.override(param, "step2")
            assertEquals("step2", awaitItem().value)

            // Update directly through provider
            localProvider.set(param, "step3")
            assertEquals("step3", awaitItem().value)

            // Update with same value - should not emit due to distinctUntilChanged
            localProvider.set(param, "step3")
            expectNoEvents()

            // Final update
            configValues.override(param, "final")
            assertEquals("final", awaitItem().value)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConfigValueTransformations() = runTest {
        val localProvider = InMemoryConfigValueProvider()
        val configValues = ConfigValues(localProvider = localProvider)
        val stringParam = ConfigParam("number_as_string", "0")

        localProvider.set(stringParam, "42")
        val configValue = configValues.getValue(stringParam)

        // Test map transformation
        val intValue = configValue.map { it.toInt() }
        assertEquals(42, intValue.value)
        assertEquals(ConfigValue.Source.LOCAL, intValue.source)

        // Test doIf with predicate
        var actionExecuted = false
        configValue.doIf(
            predicate = { it.value.length > 1 },
            action = { actionExecuted = true }
        )
        assertTrue(actionExecuted)

        // Test doIf with else action
        var elseExecuted = false
        configValue.doIf(
            predicate = { it.value.length > 10 },
            action = { },
            elseAction = { elseExecuted = true }
        )
        assertTrue(elseExecuted)
    }

    // Helper class for remote provider testing
    private class TestRemoteProvider() : RemoteConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()
        var fetchCalled = false

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
        }
    }
}
