package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagInputParserTest {

    // --- String ---

    @Test
    fun parseInput_returnsStringAsIs() {
        val param = ConfigParam(key = "s", defaultValue = "hello")
        assertEquals("world", parseInput(param, "world"))
    }

    @Test
    fun parseInput_returnsEmptyStringForEmptyInput() {
        val param = ConfigParam(key = "s", defaultValue = "hello")
        assertEquals("", parseInput(param, ""))
    }

    // --- Int ---

    @Test
    fun parseInput_parsesValidInt() {
        val param = ConfigParam(key = "i", defaultValue = 0)
        assertEquals(42, parseInput(param, "42"))
    }

    @Test
    fun parseInput_returnsNullForInvalidInt() {
        val param = ConfigParam(key = "i", defaultValue = 0)
        assertNull(parseInput(param, "abc"))
    }

    @Test
    fun parseInput_returnsNullForFloatStringWhenIntExpected() {
        val param = ConfigParam(key = "i", defaultValue = 0)
        assertNull(parseInput(param, "3.14"))
    }

    // --- Long ---

    @Test
    fun parseInput_parsesValidLong() {
        val param = ConfigParam(key = "l", defaultValue = 0L)
        assertEquals(9_000_000_000L, parseInput(param, "9000000000"))
    }

    @Test
    fun parseInput_returnsNullForInvalidLong() {
        val param = ConfigParam(key = "l", defaultValue = 0L)
        assertNull(parseInput(param, "not_a_long"))
    }

    // --- Float ---

    @Test
    fun parseInput_parsesValidFloat() {
        val param = ConfigParam(key = "f", defaultValue = 0f)
        val result = parseInput(param, "3.14")
        assertEquals(3.14f, result)
    }

    @Test
    fun parseInput_returnsNullForInvalidFloat() {
        val param = ConfigParam(key = "f", defaultValue = 0f)
        assertNull(parseInput(param, "xyz"))
    }

    // --- Double ---

    @Test
    fun parseInput_parsesValidDouble() {
        val param = ConfigParam(key = "d", defaultValue = 0.0)
        val result = parseInput(param, "2.718281828")
        assertEquals(2.718281828, result)
    }

    @Test
    fun parseInput_returnsNullForInvalidDouble() {
        val param = ConfigParam(key = "d", defaultValue = 0.0)
        assertNull(parseInput(param, "??"))
    }

    // --- Boolean (should not be parsed via text — returns null) ---

    @Test
    fun parseInput_returnsNullForBooleanParam() {
        val param = ConfigParam(key = "b", defaultValue = false)
        assertNull(parseInput(param, "true"))
    }

    // --- isScalarParam ---

    @Test
    fun isScalarParam_trueForString() {
        assertTrue(isScalarParam(ConfigParam(key = "s", defaultValue = "")))
    }

    @Test
    fun isScalarParam_trueForInt() {
        assertTrue(isScalarParam(ConfigParam(key = "i", defaultValue = 0)))
    }

    @Test
    fun isScalarParam_trueForLong() {
        assertTrue(isScalarParam(ConfigParam(key = "l", defaultValue = 0L)))
    }

    @Test
    fun isScalarParam_trueForFloat() {
        assertTrue(isScalarParam(ConfigParam(key = "f", defaultValue = 0f)))
    }

    @Test
    fun isScalarParam_trueForDouble() {
        assertTrue(isScalarParam(ConfigParam(key = "d", defaultValue = 0.0)))
    }

    @Test
    fun isScalarParam_falseForBoolean() {
        assertFalse(isScalarParam(ConfigParam(key = "b", defaultValue = false)))
    }
}
