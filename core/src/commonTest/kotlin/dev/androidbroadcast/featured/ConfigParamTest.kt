package dev.androidbroadcast.featured

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigParamTest {

    @Test
    fun testSimpleConfigParamCreation() {
        val param = SimpleConfigParam(
            key = "test_key",
            defaultValue = "default",
            valueType = String::class,
            description = "Test description",
            category = "Test category",
            sinceVersion = "1.0.0"
        )

        assertEquals("test_key", param.key)
        assertEquals("default", param.defaultValue)
        assertEquals(String::class, param.valueType)
        assertEquals("Test description", param.description)
        assertEquals("Test category", param.category)
        assertEquals("1.0.0", param.sinceVersion)
    }

    @Test
    fun testConfigParamBuilderFunction() {
        val param = ConfigParam(
            key = "test_key",
            defaultValue = 42,
            description = "Test number",
            category = "Numbers",
            sinceVersion = "1.1.0"
        )

        assertEquals("test_key", param.key)
        assertEquals(42, param.defaultValue)
        assertEquals(Int::class, param.valueType)
        assertEquals("Test number", param.description)
        assertEquals("Numbers", param.category)
        assertEquals("1.1.0", param.sinceVersion)
    }

    @Test
    fun testConfigParamWithNullOptionalFields() {
        val param = ConfigParam(
            key = "simple_key",
            defaultValue = true
        )

        assertEquals("simple_key", param.key)
        assertEquals(true, param.defaultValue)
        assertEquals(Boolean::class, param.valueType)
        assertNull(param.description)
        assertNull(param.category)
        assertNull(param.sinceVersion)
    }

    @Test
    fun testConfigParamEquality() {
        val param1 = ConfigParam("key", "value")
        val param2 = ConfigParam("key", "value")
        val param3 = ConfigParam("different_key", "value")

        assertEquals(param1, param2)
        assertNotEquals(param1, param3)
    }

    @Test
    fun testConfigParamHashCode() {
        val param1 = ConfigParam("key", "value")
        val param2 = ConfigParam("key", "value")
        val param3 = ConfigParam("different_key", "value")

        assertEquals(param1.hashCode(), param2.hashCode())
        assertNotEquals(param1.hashCode(), param3.hashCode())
    }

    @Test
    fun testConfigParamToString() {
        val param = ConfigParam(
            key = "test_key",
            defaultValue = "value",
            description = "desc",
            category = "cat",
            sinceVersion = "1.0"
        )

        val result = param.toString()
        assertTrue(result.contains("key='test_key'"))
        assertTrue(result.contains("defaultValue=value"))
        assertTrue(result.contains("category=cat"))
        assertTrue(result.contains("sinceVersion=1.0"))
        assertTrue(result.contains("description=desc"))
    }
}
