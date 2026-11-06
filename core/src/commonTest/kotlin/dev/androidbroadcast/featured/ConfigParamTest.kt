package dev.androidbroadcast.featured

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigParamTest {
    @Test
    fun testConfigParamCreation() {
        val param =
            ConfigParam(
                key = "test_key",
                defaultValue = "default",
                description = "Test description",
                category = "Test category",
                since = "1.0.0",
            )

        assertEquals("test_key", param.key)
        assertEquals("default", param.defaultValue)
        assertEquals(String::class, param.valueType)
        assertEquals("Test description", param.description)
        assertEquals("Test category", param.category)
        assertEquals("1.0.0", param.since)
    }

    @Test
    fun testConfigParamBuilderFunction() {
        val param =
            ConfigParam(
                key = "test_key",
                defaultValue = 42,
                description = "Test number",
                category = "Numbers",
                since = "1.1.0",
            )

        assertEquals("test_key", param.key)
        assertEquals(42, param.defaultValue)
        assertEquals(Int::class, param.valueType)
        assertEquals("Test number", param.description)
        assertEquals("Numbers", param.category)
        assertEquals("1.1.0", param.since)
    }

    @Test
    fun testConfigParamWithNullOptionalFields() {
        val param =
            ConfigParam(
                key = "simple_key",
                defaultValue = true,
            )

        assertEquals("simple_key", param.key)
        assertEquals(true, param.defaultValue)
        assertEquals(Boolean::class, param.valueType)
        assertNull(param.description)
        assertNull(param.category)
        assertNull(param.since)
        assertNull(param.enumConstants)
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
        val param =
            ConfigParam(
                key = "test_key",
                defaultValue = "value",
                description = "desc",
                category = "cat",
                since = "1.0",
            )

        val result = param.toString()
        assertTrue(result.contains("key='test_key'"))
        assertTrue(result.contains("defaultValue='value'"))
        assertTrue(result.contains("category='cat'"))
        assertTrue(result.contains("since='1.0'"))
        assertTrue(result.contains("description='desc'"))
    }

    @Test
    fun testEnumConstantsDefaultsToNull() {
        val param = ConfigParam(key = "flag", defaultValue = true)

        assertNull(param.enumConstants)
    }

    @Test
    fun testEqualsReturnsFalseWhenEnumConstantsDiffer() {
        val paramWithConstants =
            ConfigParam(
                key = "flag",
                defaultValue = false,
                enumConstants = listOf(true, false),
            )
        val paramWithoutConstants =
            ConfigParam(
                key = "flag",
                defaultValue = false,
            )

        assertNotEquals(paramWithConstants, paramWithoutConstants)
    }

    @Test
    fun testEqualsReturnsTrueWhenEnumConstantsAreIdentical() {
        val param1 =
            ConfigParam(
                key = "flag",
                defaultValue = false,
                enumConstants = listOf(true, false),
            )
        val param2 =
            ConfigParam(
                key = "flag",
                defaultValue = false,
                enumConstants = listOf(true, false),
            )

        assertEquals(param1, param2)
    }

    @Test
    fun testToStringIncludesEnumConstantsWhenPresent() {
        val param =
            ConfigParam(
                key = "flag",
                defaultValue = false,
                enumConstants = listOf(true, false),
            )

        assertTrue(param.toString().contains("enumConstants="))
    }

    @Test
    fun factoryWithoutScopeIsAny() {
        val param =
            ConfigParam(
                key = "FEATURE_X",
                defaultValue = "default",
                description = "desc",
            )

        assertEquals(FlagScope.ANY, param.scope)
        assertEquals("FEATURE_X", param.key)
        assertEquals("ANY:FEATURE_X", param.localStorageKey())
    }

    @Test
    fun oldConstructorParameterListIsAny() {
        val param =
            ConfigParam(
                key = "FEATURE_X",
                defaultValue = "default",
                valueType = String::class,
                description = "desc",
                category = "cat",
                since = "1.0",
                enumConstants = null,
            )

        assertEquals(FlagScope.ANY, param.scope)
        assertEquals("FEATURE_X", param.key)
    }

    @Test
    fun scopedFactoryKeepsPublicKey() {
        val local = ConfigParam("FEATURE_X", "L", FlagScope.LOCAL)
        val remote = ConfigParam(key = "FEATURE_X", defaultValue = "R", scope = FlagScope.REMOTE)

        assertEquals(FlagScope.LOCAL, local.scope)
        assertEquals(FlagScope.REMOTE, remote.scope)
        assertEquals("FEATURE_X", local.key)
        assertEquals("FEATURE_X", remote.key)
        assertEquals("LOCAL:FEATURE_X", local.localStorageKey())
        assertEquals("REMOTE:FEATURE_X", remote.localStorageKey())
        assertEquals("LOCAL:FEA:TURE", ConfigParam("FEA:TURE", "v", FlagScope.LOCAL).localStorageKey())
    }

    @Test
    fun equalsIncludesScopeAndHashCodeStaysKey() {
        val local = ConfigParam("FEATURE_X", "value", FlagScope.LOCAL)
        val remote = ConfigParam("FEATURE_X", "value", FlagScope.REMOTE)
        val sameLocal = ConfigParam("FEATURE_X", "value", FlagScope.LOCAL)

        assertEquals(sameLocal, local)
        assertNotEquals(local, remote)
        assertEquals(local.hashCode(), remote.hashCode())
        assertEquals("FEATURE_X".hashCode(), local.hashCode())
    }
}
