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

    /** All flags declared in this container. */
    public val flags: List<FlagSpec> get() = _flags.toList()

    /** Declares a [Boolean] feature flag. */
    public fun boolean(
        key: String,
        default: Boolean,
        configure: FlagSpec.() -> Unit = {},
    ) {
        _flags += FlagSpec(key, default.toString(), "Boolean").apply(configure)
    }

    /** Declares an [Int] feature flag. */
    public fun int(
        key: String,
        default: Int,
        configure: FlagSpec.() -> Unit = {},
    ) {
        _flags += FlagSpec(key, default.toString(), "Int").apply(configure)
    }

    /** Declares a [Long] feature flag. */
    public fun long(
        key: String,
        default: Long,
        configure: FlagSpec.() -> Unit = {},
    ) {
        _flags += FlagSpec(key, default.toString(), "Long").apply(configure)
    }

    /** Declares a [Float] feature flag. */
    public fun float(
        key: String,
        default: Float,
        configure: FlagSpec.() -> Unit = {},
    ) {
        _flags += FlagSpec(key, default.toString(), "Float").apply(configure)
    }

    /** Declares a [Double] feature flag. */
    public fun double(
        key: String,
        default: Double,
        configure: FlagSpec.() -> Unit = {},
    ) {
        _flags += FlagSpec(key, default.toString(), "Double").apply(configure)
    }

    /** Declares a [String] feature flag. */
    public fun string(
        key: String,
        default: String,
        configure: FlagSpec.() -> Unit = {},
    ) {
        _flags += FlagSpec(key, "\"$default\"", "String").apply(configure)
    }

    /**
     * Declares an enum-typed feature flag.
     *
     * The plugin generates a typed `ConfigParam<E>` backed by this declaration. Enum flags are
     * intentionally excluded from R8 `-assumevalues` DCE rules — the value cannot be assumed at
     * build time (it is resolved at runtime from providers).
     *
     * ## Runtime converter requirement (Android / JVM)
     *
     * Storage-backed local providers serialize values as strings and require an explicit
     * [enumConverter] registration before the first read or write of this flag. Without it the
     * provider throws [IllegalArgumentException] synchronously. Affected providers:
     *
     * - `DataStoreConfigValueProvider`
     * - `JavaPreferencesConfigValueProvider`
     * - `SharedPreferencesProviderConfig`
     *
     * Firebase Remote Config (`FirebaseConfigValueProvider`) handles enums automatically via
     * reflection — no `registerConverter` call is needed there.
     *
     * **iOS caveat:** `NSUserDefaultsConfigValueProvider` does not support enums at this time —
     * it has no converter API. Use a `String` flag as a workaround on iOS and convert the raw
     * value to your enum manually at the call site.
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
        _flags += FlagSpec(key = key, defaultValue = default, type = typeFqn).apply(configure)
    }

    /** Serialises all flags to pipe-delimited descriptors for [ResolveFlagsTask] inputs. */
    internal fun toDescriptors(): List<String> = _flags.map { it.toDescriptor() }
}
