package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlagFilterTest {
    private fun makeItem(
        key: String,
        category: String? = null,
        source: ConfigValue.Source = ConfigValue.Source.DEFAULT,
    ) = DebugFlagItem(
        param = ConfigParam(key = key, defaultValue = true, category = category),
        currentValue = true,
        overrideValue = if (source == ConfigValue.Source.LOCAL) true else null,
        source = source,
    )

    // --- query match ---

    @Test
    fun filterFlags_emptyQueryReturnsAll() {
        val items = listOf(makeItem("feature_a"), makeItem("feature_b"), makeItem("feature_c"))
        val result = filterFlags(items, query = "", overriddenOnly = false)
        assertEquals(3, result.size)
    }

    @Test
    fun filterFlags_blankQueryReturnsAll() {
        val items = listOf(makeItem("flag_x"), makeItem("flag_y"))
        val result = filterFlags(items, query = "   ", overriddenOnly = false)
        assertEquals(2, result.size)
    }

    @Test
    fun filterFlags_queryMatchesKeyContains() {
        val items = listOf(makeItem("checkout_button"), makeItem("promotions_banner"), makeItem("checkout_form"))
        val result = filterFlags(items, query = "checkout", overriddenOnly = false)
        assertEquals(2, result.size)
        assertTrue(result.all { it.key.contains("checkout") })
    }

    @Test
    fun filterFlags_queryIsCaseInsensitive() {
        val items = listOf(makeItem("Feature_LoginScreen"), makeItem("other_flag"))
        val result = filterFlags(items, query = "loginscreen", overriddenOnly = false)
        assertEquals(1, result.size)
        assertEquals("Feature_LoginScreen", result[0].key)
    }

    @Test
    fun filterFlags_queryMatchesCategoryName() {
        val items =
            listOf(
                makeItem("flag_a", category = "Checkout"),
                makeItem("flag_b", category = "Promotions"),
                makeItem("flag_c", category = "Checkout"),
            )
        val result = filterFlags(items, query = "Checkout", overriddenOnly = false)
        assertEquals(2, result.size)
    }

    @Test
    fun filterFlags_queryCategoryMatchIsCaseInsensitive() {
        val items =
            listOf(
                makeItem("flag_x", category = "FeatureArea"),
                makeItem("flag_y", category = "Other"),
            )
        val result = filterFlags(items, query = "featurearea", overriddenOnly = false)
        assertEquals(1, result.size)
        assertEquals("flag_x", result[0].key)
    }

    @Test
    fun filterFlags_noMatchReturnsEmpty() {
        val items = listOf(makeItem("flag_a"), makeItem("flag_b"))
        val result = filterFlags(items, query = "xyz_no_match", overriddenOnly = false)
        assertTrue(result.isEmpty())
    }

    // --- overriddenOnly ---

    @Test
    fun filterFlags_overriddenOnlyKeepsLocalSourceOnly() {
        val items =
            listOf(
                makeItem("local_flag", source = ConfigValue.Source.LOCAL),
                makeItem("default_flag", source = ConfigValue.Source.DEFAULT),
                makeItem("remote_flag", source = ConfigValue.Source.REMOTE),
            )
        val result = filterFlags(items, query = "", overriddenOnly = true)
        assertEquals(1, result.size)
        assertEquals("local_flag", result[0].key)
    }

    @Test
    fun filterFlags_overriddenOnlyFalseKeepsAll() {
        val items =
            listOf(
                makeItem("local_flag", source = ConfigValue.Source.LOCAL),
                makeItem("default_flag", source = ConfigValue.Source.DEFAULT),
            )
        val result = filterFlags(items, query = "", overriddenOnly = false)
        assertEquals(2, result.size)
    }

    // --- AND semantics ---

    @Test
    fun filterFlags_queryAndOverriddenOnlyBothApplied() {
        val items =
            listOf(
                makeItem("checkout_enabled", source = ConfigValue.Source.LOCAL),
                makeItem("checkout_banner", source = ConfigValue.Source.DEFAULT),
                makeItem("promotions_flag", source = ConfigValue.Source.LOCAL),
            )
        // Only "checkout_enabled" matches both: key contains "checkout" AND source is LOCAL
        val result = filterFlags(items, query = "checkout", overriddenOnly = true)
        assertEquals(1, result.size)
        assertEquals("checkout_enabled", result[0].key)
    }

    @Test
    fun filterFlags_emptyItemsReturnsEmpty() {
        val result = filterFlags(emptyList(), query = "any", overriddenOnly = false)
        assertTrue(result.isEmpty())
    }
}
