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

    /** Serialises all flags to pipe-delimited descriptors for [ResolveFlagsTask] inputs. */
    internal fun toDescriptors(): List<String> = _flags.map { it.toDescriptor() }
}
