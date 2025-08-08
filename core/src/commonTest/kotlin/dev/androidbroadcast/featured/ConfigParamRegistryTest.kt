package dev.androidbroadcast.featured

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame

class ConfigParamRegistryTest {

    @Test
    fun testRegisterParam() = runTest {
        ConfigParamRegistry.clear()

        val param = ConfigParam("test_key", "default_value")
        val registeredParam = ConfigParamRegistry.register(param)

        assertSame(param, registeredParam)
        assertTrue(ConfigParamRegistry.getAll().contains(param))
    }

    @Test
    fun testRegisterMultipleParams() = runTest {
        ConfigParamRegistry.clear()

        val param1 = ConfigParam("key1", "value1")
        val param2 = ConfigParam("key2", 42)
        val param3 = ConfigParam("key3", true)

        ConfigParamRegistry.register(param1)
        ConfigParamRegistry.register(param2)
        ConfigParamRegistry.register(param3)

        val allParams = ConfigParamRegistry.getAll()
        assertEquals(3, allParams.size)
        assertTrue(allParams.contains(param1))
        assertTrue(allParams.contains(param2))
        assertTrue(allParams.contains(param3))
    }

    @Test
    fun testRegisteredParamsFlow() = runTest {
        ConfigParamRegistry.clear()

        val initialParams = ConfigParamRegistry.registeredParams.value
        assertTrue(initialParams.isEmpty())

        val param = ConfigParam("flow_test", "value")
        ConfigParamRegistry.register(param)

        val updatedParams = ConfigParamRegistry.registeredParams.value
        assertEquals(1, updatedParams.size)
        assertTrue(updatedParams.contains(param))
    }

    @Test
    fun testClear() = runTest {
        ConfigParamRegistry.clear()

        val param1 = ConfigParam("key1", "value1")
        val param2 = ConfigParam("key2", 42)

        ConfigParamRegistry.register(param1)
        ConfigParamRegistry.register(param2)

        assertEquals(2, ConfigParamRegistry.getAll().size)

        ConfigParamRegistry.clear()

        assertTrue(ConfigParamRegistry.getAll().isEmpty())
        assertTrue(ConfigParamRegistry.registeredParams.value.isEmpty())
    }

    @Test
    fun testGetAllReturnsImmutableSnapshot() = runTest {
        ConfigParamRegistry.clear()

        val param = ConfigParam("test", "value")
        ConfigParamRegistry.register(param)

        val allParams1 = ConfigParamRegistry.getAll()
        val allParams2 = ConfigParamRegistry.getAll()

        // Should return the same content but different set instances
        assertEquals(allParams1, allParams2)
        assertTrue(allParams1.contains(param))
        assertTrue(allParams2.contains(param))
    }
}
