package dev.androidbroadcast.featured.debugui

/**
 * Groups a list of [DebugFlagItem] entries by their [DebugFlagItem.category].
 *
 * Items without a category are grouped under a `null` key.
 * Order within each group is preserved.
 *
 * @return A [Map] from category name (or null) to the list of items in that category.
 */
public fun groupFlagsByCategory(
    items: List<DebugFlagItem<*>>,
): Map<String?, List<DebugFlagItem<*>>> = items.groupBy { it.category }
