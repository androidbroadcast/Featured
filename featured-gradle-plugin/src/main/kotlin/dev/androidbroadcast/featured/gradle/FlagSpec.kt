package dev.androidbroadcast.featured.gradle

/**
 * Descriptor for a single feature flag declared in the `featured { }` Gradle DSL.
 *
 * @property key The configuration key string (e.g. `"dark_mode"`). Acts as the unique identifier.
 * @property defaultValue The default value serialised to a string (e.g. `"false"`, `"42"`).
 * @property type The Kotlin type name: `"Boolean"`, `"Int"`, `"Long"`, `"Float"`, `"Double"`, or `"String"`.
 * @property description Optional human-readable description passed to the generated [ConfigParam].
 * @property category Optional grouping label shown in the debug UI.
 * @property expiresAt Optional ISO-8601 date (`"YYYY-MM-DD"`) after which the flag is considered stale.
 */
public class FlagSpec(
    public val key: String,
    public val defaultValue: String,
    public val type: String,
) {
    public var description: String? = null
    public var category: String? = null
    public var expiresAt: String? = null

    /** Serialises this spec to a pipe-delimited descriptor consumed by [ResolveFlagsTask]. */
    internal fun toDescriptor(): String = "$key|$defaultValue|$type|${description.orEmpty()}|${category.orEmpty()}|${expiresAt.orEmpty()}"
}
