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

    private val _discards = mutableListOf<String>()

    /**
     * R8/ProGuard class specifications that must be discarded from release binaries when this
     * flag is at its (false) default. Populated via [discard]; consumed by
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
     * Only valid on a **local boolean** flag whose default is `false`: a runtime-resolved
     * (remote) flag is never pinned at build time, and a flag that defaults to `true` keeps its
     * guarded code, so in neither case can the discard be guaranteed.
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
                "for a boolean flag whose default is false."
        }
        require(defaultValue == "false") {
            "discard(...) requires boolean flag \"$key\" to have default = false (got $defaultValue). " +
                "A flag that defaults to true keeps its guarded code in release builds, so that code " +
                "cannot be verified as discarded."
        }
        val spec = classSpec.trim()
        require(spec.isNotEmpty()) { "discard(...) classSpec for flag \"$key\" must not be blank." }
        _discards += spec
    }

    /** Serialises this spec to a pipe-delimited descriptor consumed by [ResolveFlagsTask]. */
    internal fun toDescriptor(): String = "$key|$defaultValue|$type|${description.orEmpty()}|${category.orEmpty()}|${expiresAt.orEmpty()}"
}
