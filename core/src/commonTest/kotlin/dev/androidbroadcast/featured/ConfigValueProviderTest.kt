package dev.androidbroadcast.featured

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigValueProviderTest {

    // Test implementation of LocalConfigValueProvider
    private class TestLocalProvider() : LocalConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
            return storage[param.key]?.let { value ->
                ConfigValue(value as T, ConfigValue.Source.LOCAL)
            }
        }

        override suspend fun <T : Any> set(param: ConfigParam<T>, value: T) {
            storage[param.key] = value
        }

        override fun <T : Any> observe(param: ConfigParam<T>) = throw NotImplementedError()
    }

    // Test implementation of RemoteConfigValueProvider
    private class TestRemoteProvider() : RemoteConfigValueProvider {
        private val storage = mutableMapOf<String, Any>()
        var fetchCalled = false
        var lastActivateValue = false

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
            lastActivateValue = activate
        }
    }

    @Test
    fun testLocalProviderBasicOperations() = runTest {
        val provider = TestLocalProvider()
        val param = ConfigParam("test_key", "default")

        // Initially should return null
        assertNull(provider.get(param))

        // Set value and retrieve
        provider.set(param, "test_value")
        val result = provider.get(param)

        assertEquals("test_value", result?.value)
        assertEquals(ConfigValue.Source.LOCAL, result?.source)
    }

    @Test
    fun testRemoteProviderBasicOperations() = runTest {
        val provider = TestRemoteProvider()
        val param = ConfigParam("test_key", "default")

        // Initially should return null
        assertNull(provider.get(param))

        // Set mock value and retrieve
        provider.setMockValue("test_key", "remote_value")
        val result = provider.get(param)

        assertEquals("remote_value", result?.value)
        assertEquals(ConfigValue.Source.REMOTE, result?.source)
    }

    @Test
    fun testRemoteProviderFetch() = runTest {
        val provider = TestRemoteProvider()

        // Test fetch with activate = true (default)
        provider.fetch()
        assertEquals(true, provider.fetchCalled)
        assertEquals(true, provider.lastActivateValue)

        // Reset and test with activate = false
        provider.fetchCalled = false
        provider.fetch(activate = false)
        assertEquals(true, provider.fetchCalled)
        assertEquals(false, provider.lastActivateValue)
    }

    @Test
    fun testDifferentValueTypes() = runTest {
        val provider = TestLocalProvider()

        // String parameter
        val stringParam = ConfigParam("string_key", "default")
        provider.set(stringParam, "string_value")
        assertEquals("string_value", provider.get(stringParam)?.value)

        // Int parameter
        val intParam = ConfigParam("int_key", 0)
        provider.set(intParam, 42)
        assertEquals(42, provider.get(intParam)?.value)

        // Boolean parameter
        val boolParam = ConfigParam("bool_key", false)
        provider.set(boolParam, true)
        assertEquals(true, provider.get(boolParam)?.value)

        // Double parameter
        val doubleParam = ConfigParam("double_key", 0.0)
        provider.set(doubleParam, 3.14)
        assertEquals(3.14, provider.get(doubleParam)?.value)
    }
}
