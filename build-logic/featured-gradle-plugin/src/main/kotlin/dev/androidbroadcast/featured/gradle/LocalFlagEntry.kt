package dev.androidbroadcast.featured.gradle

/**
 * Represents a single feature flag declared via the `featured { }` Gradle DSL.
 *
 * @property key The configuration key string (e.g. `"dark_mode"`).
 * @property defaultValue The default value as a raw string (e.g. `"false"`, `"42"`).
 * @property type The Kotlin type name: `"Boolean"`, `"Int"`, `"Long"`, `"Float"`, `"Double"`, `"String"`,
 *   or a fully-qualified enum class name (e.g. `"com.example.CheckoutVariant"`).
 * @property moduleName The Gradle module path that declares this flag (e.g. `":feature:checkout"`).
 * @property propertyName The camelCase property name derived from [key] (e.g. `"darkMode"`).
 * @property flagType Either `"local"` or `"remote"`.
 * @property description Optional human-readable description, passed to the generated [ConfigParam].
 * @property category Optional grouping category shown in the debug UI.
 * @property expiresAt Optional ISO-8601 date string (`"YYYY-MM-DD"`) after which the flag is considered stale.
 */
public data class LocalFlagEntry(
    public val key: String,
    public val defaultValue: String,
    public val type: String,
    public val moduleName: String,
    public val propertyName: String = "",
    public val flagType: String = FLAG_TYPE_LOCAL,
    public val description: String? = null,
    public val category: String? = null,
    public val expiresAt: String? = null,
) {
    public val isLocal: Boolean get() = flagType == FLAG_TYPE_LOCAL

    /**
     * Returns `true` when this flag's type is an enum (i.e. a fully-qualified class name
     * containing a `.`) rather than a built-in Kotlin primitive or `String`.
     *
     * Enum flags require special handling in code generators:
     * - ProGuard `-assumevalues` rules are skipped (enum values are not assumable at build time).
     * - iOS `const val` generation is skipped (`const` only supports primitive types and `String`).
     * - The generated `ConfigParam` default expression uses `TypeFqn.CONSTANT_NAME` syntax.
     */
    public val isEnum: Boolean get() = '.' in type

    public companion object {
        public const val FLAG_TYPE_LOCAL: String = "local"
        public const val FLAG_TYPE_REMOTE: String = "remote"
        internal const val GENERATED_LOCAL_OBJECT = "GeneratedLocalFlags"
        internal const val GENERATED_REMOTE_OBJECT = "GeneratedRemoteFlags"
    }
}
