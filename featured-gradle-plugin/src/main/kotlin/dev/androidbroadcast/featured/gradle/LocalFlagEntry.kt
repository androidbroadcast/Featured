package dev.androidbroadcast.featured.gradle

/**
 * Represents a single `@LocalFlag`-annotated [dev.androidbroadcast.featured.ConfigParam]
 * discovered during source scanning.
 *
 * @property key The configuration key string passed to [ConfigParam].
 * @property defaultValue The default value as a raw string extracted from source.
 * @property type The inferred Kotlin type name (e.g. "Boolean", "String", "Int", "Double").
 * @property moduleName The Gradle module (project path) that declares this flag.
 * @property propertyName The Kotlin property name of the [ConfigParam] declaration (e.g. `"darkTheme"`).
 * @property ownerName The simple name of the enclosing `object` or `companion object`, or `null`
 *   if the declaration is top-level.
 */
public data class LocalFlagEntry(
    public val key: String,
    public val defaultValue: String,
    public val type: String,
    public val moduleName: String,
    public val propertyName: String = "",
    public val ownerName: String? = null,
) {
    /**
     * Returns the Kotlin reference expression used in the generated `FlagRegistry.register(...)` call.
     *
     * - If [ownerName] is non-null: `"OwnerName.propertyName"`
     * - Otherwise: `"propertyName"` (top-level declaration)
     *
     * Returns an empty string when [propertyName] is blank, signalling that the entry
     * cannot produce a valid compile-time reference (e.g. when scanned from legacy data
     * without property-name information).
     */
    public val kotlinReference: String
        get() =
            when {
                propertyName.isBlank() -> ""
                ownerName != null -> "$ownerName.$propertyName"
                else -> propertyName
            }
}
