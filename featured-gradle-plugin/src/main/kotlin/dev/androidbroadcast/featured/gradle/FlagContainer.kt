package dev.androidbroadcast.featured.gradle

/**
 * DSL receiver for declaring feature flags inside a `localFlags { }` or `remoteFlags { }` block.
 *
 * Usage:
 * ```kotlin
 * featured {
 *     localFlags {
 *         boolean("dark_mode", default = false) { category = "UI" }
 *         int("retry_count", default = 3)
 *         string("api_base_url", default = "https://example.com")
 *         enum("checkout_variant", typeFqn = "com.example.CheckoutVariant", default = "LEGACY")
 *     }
 *     remoteFlags {
 *         boolean("promo_banner_enabled", default = false) {
 *             description = "Show promotional banner"
 *             expiresAt = "2026-12-01"
 *         }
 *     }
 * }
 * ```
 */
public class FlagContainer {
    private val _flags = mutableListOf<FlagSpec>()

    /**
     * Whether flags declared here are local. [FeaturedExtension] marks the remote container
     * before any configure block, so [FlagSpec.defaultFor] can reject remote flags.
     */
    internal var localSection: Boolean = true

    /** All flags declared in this container. */
    public val flags: List<FlagSpec> get() = _flags.toList()

    /**
     * Code-generation overrides for this container's flags (class name, package,
     * visibility). Values not set here fall back to the module-wide defaults in
     * `featured { generation { } }`.
     */
    public val generation: GenerationSettings = GenerationSettings()

    /** Configures code-generation overrides for this container's flags. */
    public fun generation(configure: GenerationSettings.() -> Unit): Unit = generation.configure()

    /** Declares a [Boolean] feature flag. */
    public fun boolean(
        key: String,
        default: Boolean,
        configure: FlagSpec.() -> Unit = {},
    ) {
        addFlag(FlagSpec(key, default.toString(), "Boolean"), configure)
    }

    /** Declares an [Int] feature flag. */
    public fun int(
        key: String,
        default: Int,
        configure: FlagSpec.() -> Unit = {},
    ) {
        addFlag(FlagSpec(key, default.toString(), "Int"), configure)
    }

    /** Declares a [Long] feature flag. */
    public fun long(
        key: String,
        default: Long,
        configure: FlagSpec.() -> Unit = {},
    ) {
        addFlag(FlagSpec(key, default.toString(), "Long"), configure)
    }

    /** Declares a [Float] feature flag. */
    public fun float(
        key: String,
        default: Float,
        configure: FlagSpec.() -> Unit = {},
    ) {
        addFlag(FlagSpec(key, default.toString(), "Float"), configure)
    }

    /** Declares a [Double] feature flag. */
    public fun double(
        key: String,
        default: Double,
        configure: FlagSpec.() -> Unit = {},
    ) {
        addFlag(FlagSpec(key, default.toString(), "Double"), configure)
    }

    /** Declares a [String] feature flag. */
    public fun string(
        key: String,
        default: String,
        configure: FlagSpec.() -> Unit = {},
    ) {
        addFlag(FlagSpec(key, "\"$default\"", "String"), configure)
    }

    /**
     * Declares an enum-typed feature flag.
     *
     * The plugin generates a typed `ConfigParam<E>` backed by this declaration. Enum flags are
     * intentionally excluded from R8 `-assumevalues` DCE rules — the value cannot be assumed at
     * build time (it is resolved at runtime from providers).
     *
     * ## Runtime converter requirement
     *
     * Storage-backed local providers serialize values as strings and require an explicit
     * [enumConverter] registration before the first read or write of this flag. Without it the
     * provider throws [IllegalArgumentException] synchronously. Affected providers:
     *
     * - `DataStoreConfigValueProvider`
     * - `JavaPreferencesConfigValueProvider`
     * - `NSUserDefaultsConfigValueProvider`
     * - `SharedPreferencesProviderConfig`
     *
     * Firebase Remote Config (`FirebaseConfigValueProvider`) handles enums automatically via
     * reflection — no `registerConverter` call is needed there.
     *
     * ## Example
     *
     * ```kotlin
     * // Gradle DSL — declaration
     * featured {
     *     localFlags {
     *         enum(
     *             key = "checkout_variant",
     *             typeFqn = "com.example.CheckoutVariant",
     *             default = "LEGACY",
     *         )
     *     }
     * }
     * ```
     *
     * ```kotlin
     * // Runtime — required wiring for non-Firebase local providers
     * val provider = DataStoreConfigValueProvider(dataStore).apply {
     *     registerConverter(enumConverter<CheckoutVariant>())
     * }
     * val configValues = ConfigValues(localProvider = provider)
     * ```
     *
     * @param key The configuration key string (e.g. `"checkout_variant"`).
     * @param typeFqn The fully-qualified Kotlin class name of the enum (e.g. `"com.example.CheckoutVariant"`).
     * @param default The name of the default enum constant (e.g. `"LEGACY"`).
     * @param configure Optional block to set [FlagSpec.description], [FlagSpec.category], or [FlagSpec.expiresAt].
     */
    public fun enum(
        key: String,
        typeFqn: String,
        default: String,
        configure: FlagSpec.() -> Unit = {},
    ) {
        require('.' in typeFqn) {
            "typeFqn must be a fully-qualified class name (e.g. \"com.example.MyEnum\"), got \"$typeFqn\""
        }
        addFlag(FlagSpec(key = key, defaultValue = default, type = typeFqn), configure)
    }

    /**
     * Marks the spec with this container's local/remote section, then applies [configure].
     * The mark is visible to [FlagSpec.defaultFor] inside the block.
     */
    private fun addFlag(
        spec: FlagSpec,
        configure: FlagSpec.() -> Unit,
    ) {
        spec.declaredInLocalSection = localSection
        spec.configure()
        _flags += spec
    }

    /** Serialises all flags to pipe-delimited descriptors for [ResolveFlagsTask] inputs. */
    internal fun toDescriptors(): List<String> = _flags.map { it.toDescriptor() }

    /**
     * Serialises every `discard(...)` target to a `flagKey|classSpec` descriptor for
     * [GenerateCheckDiscardRulesTask] inputs. Flags without discard targets contribute nothing.
     */
    internal fun discardDescriptors(): List<String> = _flags.flatMap { flag -> flag.discards.map { spec -> "${flag.key}|$spec" } }

    /**
     * One `key|buildType|serializedValue` line per [FlagSpec.defaultFor] override. The value is
     * the remainder after the second pipe. Empty means every build type uses the base default.
     */
    internal fun buildTypeDefaultDescriptors(): List<String> =
        _flags.flatMap { flag ->
            flag.buildTypeDefaults.map { (buildType, value) ->
                BuildTypeDefaults.descriptor(flag.key, buildType, value)
            }
        }
}
