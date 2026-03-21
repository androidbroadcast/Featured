package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagGroupingTest {

    private fun makeItem(
        key: String,
        category: String? = null,
        source: ConfigValue.Source = ConfigValue.Source.DEFAULT,
    ) = DebugFlagItem(
        param = ConfigParam(key = key, defaultValue = true, category = category),
        currentValue = true,
        overrideValue = null,
        source = source,
    )

    @Test
    fun groupByCategory_groupsItemsByCategory() {
        val items = listOf(
            makeItem("flag_a", category = "UI"),
            makeItem("flag_b", category = "Network"),
            makeItem("flag_c", category = "UI"),
        )

        val grouped = groupFlagsByCategory(items)

        assertEquals(setOf("UI", "Network"), grouped.keys)
        assertEquals(2, grouped["UI"]?.size)
        assertEquals(1, grouped["Network"]?.size)
    }

    @Test
    fun groupByCategory_placesNullCategoryUnderNullKey() {
        val items = listOf(
            makeItem("flag_x", category = null),
            makeItem("flag_y", category = "Experimental"),
        )

        val grouped = groupFlagsByCategory(items)

        assertTrue(grouped.containsKey(null))
        assertEquals(1, grouped[null]?.size)
        assertEquals("flag_x", grouped[null]?.first()?.key)
    }

    @Test
    fun groupByCategory_emptyListReturnsEmptyMap() {
        val grouped = groupFlagsByCategory(emptyList())
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun groupByCategory_preservesOrderWithinGroup() {
        val items = listOf(
            makeItem("flag_1", category = "A"),
            makeItem("flag_2", category = "A"),
            makeItem("flag_3", category = "A"),
        )

        val grouped = groupFlagsByCategory(items)
        val keys = grouped["A"]?.map { item -> item.key }

        assertEquals(listOf("flag_1", "flag_2", "flag_3"), keys)
    }

    @Test
    fun groupByCategory_singleItemNoCategory() {
        val items = listOf(makeItem("solo"))
        val grouped = groupFlagsByCategory(items)

        assertNull(grouped.keys.single())
        assertEquals("solo", grouped[null]?.single()?.key)
    }
}
