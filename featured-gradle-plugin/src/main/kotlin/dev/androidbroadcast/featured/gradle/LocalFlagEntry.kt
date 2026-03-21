package dev.androidbroadcast.featured.gradle

/**
 * Represents a single `@LocalFlag`-annotated [dev.androidbroadcast.featured.ConfigParam]
 * discovered during source scanning.
 *
 * @property key The configuration key string passed to [ConfigParam].
 * @property defaultValue The default value as a raw string extracted from source.
 * @property type The inferred Kotlin type name (e.g. "Boolean", "String", "Int", "Double").
 * @property moduleName The Gradle module (project path) that declares this flag.
 */
public data class LocalFlagEntry(
    public val key: String,
    public val defaultValue: String,
    public val type: String,
    public val moduleName: String,
)
