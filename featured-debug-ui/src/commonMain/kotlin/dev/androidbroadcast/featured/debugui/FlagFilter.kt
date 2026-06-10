package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigValue

/**
 * Filters [items] by [query] and [overriddenOnly].
 *
 * - Query match: item key or category contains [query] (case-insensitive). Empty query matches all.
 * - [overriddenOnly]: when true, keeps only items whose source is [ConfigValue.Source.LOCAL].
 * - Both conditions are AND-ed.
 */
internal fun filterFlags(
    items: List<DebugFlagItem<*>>,
    query: String,
    overriddenOnly: Boolean,
): List<DebugFlagItem<*>> {
    val trimmed = query.trim()
    return items.filter { item ->
        val matchesQuery =
            trimmed.isEmpty() ||
                item.key.contains(trimmed, ignoreCase = true) ||
                item.category?.contains(trimmed, ignoreCase = true) == true
        val matchesOverride = !overriddenOnly || item.source == ConfigValue.Source.LOCAL
        matchesQuery && matchesOverride
    }
}
