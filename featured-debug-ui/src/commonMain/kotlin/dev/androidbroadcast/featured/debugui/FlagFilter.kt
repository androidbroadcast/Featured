package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
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

/**
 * A snapshot of a single overridden flag: the param to reset and the value to restore on undo.
 */
internal data class ResetEntry<T : Any>(
    val param: ConfigParam<T>,
    val previousValue: T,
)

/**
 * Builds a reset plan from [items]: returns one [ResetEntry] per item that has
 * [DebugFlagItem.isOverridden] == true. The [ResetEntry.previousValue] captures the
 * current override value at the moment this function is called, so undo can restore it.
 *
 * An empty list means no items are overridden — "Reset all" should be disabled.
 */
internal fun buildResetPlan(items: List<DebugFlagItem<*>>): List<ResetEntry<*>> =
    items
        .filter { it.isOverridden }
        .mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val typedItem = item as? DebugFlagItem<Any> ?: return@mapNotNull null
            val overrideValue = typedItem.overrideValue ?: return@mapNotNull null
            ResetEntry(param = typedItem.param, previousValue = overrideValue)
        }
