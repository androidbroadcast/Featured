package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryConfigValueProviderTest {

    @Test
    fun testGetNonExistentParam() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("non_existent", "default")

        val result = provider.get(param)
        assertNull(result)
    }

    @Test
    fun testSetAndGetStringParam() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("string_key", "default")

        provider.set(param, "test_value")
        val result = provider.get(param)

        assertEquals("test_value", result?.value)
        assertEquals(ConfigValue.Source.LOCAL, result?.source)
    }

    @Test
    fun testSetAndGetIntParam() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("int_key", 0)

        provider.set(param, 42)
        val result = provider.get(param)

        assertEquals(42, result?.value)
        assertEquals(ConfigValue.Source.LOCAL, result?.source)
    }

    @Test
    fun testSetAndGetBooleanParam() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("bool_key", false)

        provider.set(param, true)
        val result = provider.get(param)

        assertEquals(true, result?.value)
        assertEquals(ConfigValue.Source.LOCAL, result?.source)
    }

    @Test
    fun testOverwriteExistingValue() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("key", "initial")

        provider.set(param, "first_value")
        provider.set(param, "second_value")

        val result = provider.get(param)
        assertEquals("second_value", result?.value)
    }

    @Test
    fun testClear() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("key", "default")

        provider.set(param, "value")
        assertEquals("value", provider.get(param)?.value)

        provider.clear()
        assertNull(provider.get(param))
    }

    @Test
    fun testObserveInitialValue() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("observe_key", "default")

        provider.set(param, "initial_value")

        provider.observe(param).test {
            val emission = awaitItem()
            assertEquals("initial_value", emission.value)
            assertEquals(ConfigValue.Source.LOCAL, emission.source)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testObserveValueChanges() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("observe_key", "default")

        provider.set(param, "initial")

        provider.observe(param).test {
            // Initial value
            assertEquals("initial", awaitItem().value)

            // Update value
            provider.set(param, "updated")
            assertEquals("updated", awaitItem().value)

            // Update again
            provider.set(param, "final")
            assertEquals("final", awaitItem().value)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testObserveNonExistentParam() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param = ConfigParam("non_existent", "default")

        provider.observe(param).test {
            // Should not emit anything initially since param doesn't exist
            provider.set(param, "new_value")

            val emission = awaitItem()
            assertEquals("new_value", emission.value)
            assertEquals(ConfigValue.Source.LOCAL, emission.source)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testObserveMultipleParams() = runTest {
        val provider = InMemoryConfigValueProvider()
        val param1 = ConfigParam("key1", "default1")
        val param2 = ConfigParam("key2", "default2")

        provider.set(param1, "value1")
        provider.set(param2, "value2")

        provider.observe(param1).test {
            assertEquals("value1", awaitItem().value)

            // Update param2 - should not affect param1 observer
            provider.set(param2, "updated2")

            // Update param1 - should affect param1 observer
            provider.set(param1, "updated1")
            assertEquals("updated1", awaitItem().value)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
