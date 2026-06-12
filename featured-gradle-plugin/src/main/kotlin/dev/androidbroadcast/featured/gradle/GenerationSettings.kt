package dev.androidbroadcast.featured.gradle

/**
 * DSL receiver for a `generation { }` block customising the generated code.
 *
 * The block is available in two places with fallback semantics — a value not set in a
 * section falls back to the module-wide default, which in turn falls back to the built-in
 * default:
 *
 * ```kotlin
 * featured {
 *     generation { // module-wide defaults
 *         packageName = "com.example.checkout.flags" // default: dev.androidbroadcast.featured.generated
 *         visibility = FeaturedVisibility.INTERNAL   // default: INTERNAL
 *     }
 *     localFlags {
 *         generation { // overrides for this section only
 *             className = "CheckoutLocalFlags"       // default: GeneratedLocalFlags<ModuleSuffix>
 *             packageName = "com.example.checkout.local"
 *             visibility = FeaturedVisibility.PUBLIC
 *         }
 *         boolean("dark_mode", default = false)
 *     }
 * }
 * ```
 *
 * All properties are `null` by default, meaning "not overridden here".
 */
public class GenerationSettings {
    /**
     * Name of the generated flag object. Replaces the entire default name — no
     * module-path suffix is appended. The generated `.kt` file is named after it.
     *
     * Only meaningful inside `localFlags { }` / `remoteFlags { }`; setting it in the
     * top-level `featured { generation { } }` block fails the build (there are two
     * generated objects, so a single shared name would always collide).
     *
     * The default names carry a module-derived suffix that guarantees a unique JVM class
     * name per module. With a custom name that responsibility shifts to you: two modules
     * generating the same package + class name produce duplicate-class errors when
     * assembled into the same JAR or DEX.
     */
    public var className: String? = null

    /**
     * Package of the generated sources (flag objects, `ConfigValues` extensions, and the
     * iOS expect/actual `const val` files). Default: `dev.androidbroadcast.featured.generated`.
     */
    public var packageName: String? = null

    /**
     * Visibility of the generated flag object and its `ConfigValues` extension functions.
     * Default: [FeaturedVisibility.INTERNAL].
     *
     * Does not affect the iOS expect/actual `const val` declarations — those stay `public`
     * as required for cross-source-set expect/actual matching.
     */
    public var visibility: FeaturedVisibility? = null
}
