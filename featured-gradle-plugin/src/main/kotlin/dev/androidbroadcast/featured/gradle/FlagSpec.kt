package dev.androidbroadcast.featured.gradle

/**
 * Descriptor for a single feature flag declared in the `featured { }` Gradle DSL.
 *
 * @property key The configuration key string (e.g. `"dark_mode"`). Acts as the unique identifier.
 * @property defaultValue The default value serialised to a string (e.g. `"false"`, `"42"`).
 * @property type The Kotlin type name: `"Boolean"`, `"Int"`, `"Long"`, `"Float"`, `"Double"`, `"String"`,
 *   or a fully-qualified enum class name (e.g. `"com.example.CheckoutVariant"`).
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

    /**
     * Set by [FlagContainer] before the configure block. Remote containers reject [defaultFor].
     */
    internal var declaredInLocalSection: Boolean = true

    private val buildTypeDefaultValues = linkedMapOf<String, String>()

    /**
     * Build-type overrides, keyed by the trimmed build-type name. Values use the same
     * serialization as [defaultValue]. Empty means every build type uses the base default.
     */
    internal val buildTypeDefaults: Map<String, String> get() = buildTypeDefaultValues.toMap()

    private val _discards = mutableListOf<String>()

    /**
     * R8/ProGuard class specifications that must be discarded from release binaries when this
     * flag's release effective default is `false`. Populated via [discard]; consumed by
     * [GenerateCheckDiscardRulesTask] to emit `-checkdiscard` rules.
     */
    public val discards: List<String> get() = _discards.toList()

    /**
     * Asserts that [classSpec] is dead-code-eliminated from release binaries when this flag is
     * disabled, by emitting an R8 `-checkdiscard class <classSpec> { *; }` rule.
     *
     * This is the verification companion to the `-assumevalues` rule generated for the flag:
     * `-assumevalues` pins the flag to `false` so R8 drops the disabled branch, and
     * `-checkdiscard` fails the build if anything (a forgotten DI reference, a manifest entry,
     * reflection, an over-broad `-keep`) keeps the guarded code alive. The failure surfaces at
     * build time — `Discard checks failed` plus the retention path — instead of in production.
     *
     * Only valid on a **local boolean** flag whose release effective default is `false`. A
     * runtime-resolved (remote) flag is never pinned at build time. A flag that is `true` in the
     * release build type keeps its guarded code, so the discard cannot be guaranteed. The release
     * check runs after the configure block, so `discard(...)` and `defaultFor("release", ...)`
     * may appear in either order. A debug override does not satisfy the check.
     *
     * Repeatable to list several class specifications for the same flag:
     * ```kotlin
     * boolean("new_checkout", default = false) {
     *     discard("com.example.checkout.newflow.**")
     *     discard("com.example.checkout.NewCheckoutEntryPoint")
     * }
     * ```
     *
     * @param classSpec An R8/ProGuard class specification, e.g. `com.example.featurex.**`.
     */
    public fun discard(classSpec: String) {
        require(type == "Boolean") {
            "discard(...) is only supported on boolean flags, but flag \"$key\" has type \"$type\". " +
                "-checkdiscard verifies code is removed while a flag is off, which is only well-defined " +
                "for a boolean flag whose release effective default is false."
        }
        val spec = classSpec.trim()
        require(spec.isNotEmpty()) { "discard(...) classSpec for flag \"$key\" must not be blank." }
        _discards += spec
    }

    /**
     * Sets a different declared default for one Android build type. Unspecified build types
     * keep [defaultValue]. This does not write a stored override.
     *
     * The stored name is [buildType] trimmed. Matching is case-sensitive. A second call for the
     * same trimmed name fails. The value's type must match this flag. An enum value is the
     * constant name, the same form as `enum(..., default = "LEGACY")`.
     *
     * Only valid inside `localFlags`. The name is checked against the project's Android build
     * types after the Android DSL is finalized, so either block may come first in the script.
     */
    public fun defaultFor(
        buildType: String,
        value: Boolean,
    ): Unit = storeDefaultFor(buildType, value)

    /** @see defaultFor */
    public fun defaultFor(
        buildType: String,
        value: Int,
    ): Unit = storeDefaultFor(buildType, value)

    /** @see defaultFor */
    public fun defaultFor(
        buildType: String,
        value: Long,
    ): Unit = storeDefaultFor(buildType, value)

    /** @see defaultFor */
    public fun defaultFor(
        buildType: String,
        value: Float,
    ): Unit = storeDefaultFor(buildType, value)

    /** @see defaultFor */
    public fun defaultFor(
        buildType: String,
        value: Double,
    ): Unit = storeDefaultFor(buildType, value)

    /**
     * Build-type default for a `string` flag and for an enum flag. The caller's [value] does
     * not include the quotes `string(...)` stores. An enum value is trimmed and must not be blank.
     *
     * @see defaultFor
     */
    public fun defaultFor(
        buildType: String,
        value: String,
    ): Unit = storeDefaultFor(buildType, value)

    /**
     * Effective default of [buildType]: the override for that name when one was declared,
     * otherwise [defaultValue].
     */
    internal fun effectiveDefault(buildType: String): String = BuildTypeDefaults.effective(defaultValue, buildTypeDefaults, buildType)

    private fun storeDefaultFor(
        buildType: String,
        value: Any,
    ) {
        require(declaredInLocalSection) {
            "defaultFor(...) is only supported on local flags, but flag \"$key\" is remote. " +
                "A remote flag keeps one default and is not pinned per Android build type."
        }
        require(valueMatchesDeclaredType(value)) {
            "defaultFor(...) value type does not match flag \"$key\" (declared type \"$type\")."
        }
        val name = buildType.trim()
        require(name.isNotEmpty()) {
            "defaultFor(...) build type for flag \"$key\" must not be blank."
        }
        require(name !in buildTypeDefaultValues) {
            "defaultFor(...) for flag \"$key\" declares build type \"$name\" more than once."
        }
        buildTypeDefaultValues[name] = serializeMatched(value)
    }

    private fun valueMatchesDeclaredType(value: Any): Boolean =
        when (type) {
            "Boolean" -> value is Boolean
            "Int" -> value is Int
            "Long" -> value is Long
            "Float" -> value is Float
            "Double" -> value is Double
            "String" -> value is String
            else -> '.' in type && value is String
        }

    private fun serializeMatched(value: Any): String =
        when {
            type == "String" && value is String -> {
                "\"$value\""
            }

            '.' in type && value is String -> {
                val constant = value.trim()
                require(constant.isNotEmpty()) {
                    "defaultFor(...) enum value for flag \"$key\" must not be blank."
                }
                constant
            }

            else -> {
                value.toString()
            }
        }

    /** Serialises this spec to a pipe-delimited descriptor consumed by [ResolveFlagsTask]. */
    internal fun toDescriptor(): String = "$key|$defaultValue|$type|${description.orEmpty()}|${category.orEmpty()}|${expiresAt.orEmpty()}"
}
