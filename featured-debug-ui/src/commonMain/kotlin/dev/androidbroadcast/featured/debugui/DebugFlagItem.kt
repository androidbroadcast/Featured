package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue

/**
 * Represents a single feature flag entry shown in the debug UI.
 *
 * @param T The type of the flag value.
 * @property param The underlying [ConfigParam] describing the flag.
 * @property currentValue The value currently in effect (local override or remote or default).
 * @property overrideValue The locally overridden value, or null if not overridden.
 * @property source Where the current value originates from (DEFAULT, LOCAL, REMOTE, etc.).
 */
public data class DebugFlagItem<T : Any>(
    public val param: ConfigParam<T>,
    public val currentValue: T,
    public val overrideValue: T?,
    public val source: ConfigValue.Source = ConfigValue.Source.DEFAULT,
) {
    /** The unique key that identifies this flag. */
    public val key: String get() = param.key

    /** The default value declared on the param. */
    public val defaultValue: T get() = param.defaultValue

    /** The optional human-readable description of this flag. */
    public val description: String? get() = param.description

    /** The optional category/group this flag belongs to. */
    public val category: String? get() = param.category

    /** Whether this flag currently has a local override applied. */
    public val isOverridden: Boolean get() = overrideValue != null
}
