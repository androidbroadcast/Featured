package dev.androidbroadcast.featured.gradle

/**
 * Gradle DSL extension for the `dev.androidbroadcast.featured` plugin.
 *
 * Declare all feature flags here — both local (device-only overrides) and remote
 * (controlled via a remote config service). The plugin generates typed `ConfigParam`
 * objects, ergonomic extension functions, and R8 dead-code-elimination rules from
 * these declarations.
 *
 * Usage in `build.gradle.kts`:
 * ```kotlin
 * plugins { id("dev.androidbroadcast.featured") }
 *
 * featured {
 *     localFlags {
 *         boolean("dark_mode", default = false) { category = "UI" }
 *         int("max_retries", default = 3)
 *     }
 *     remoteFlags {
 *         boolean("promo_banner_enabled", default = false) {
 *             description = "Show promotional banner"
 *             expiresAt = "2026-12-01"
 *         }
 *         string("welcome_message", default = "Hello!")
 *     }
 * }
 * ```
 *
 * The plugin generates for each module:
 * - `GeneratedLocalFlags` / `GeneratedRemoteFlags` — typed `ConfigParam` instances
 * - Extension functions: `ConfigValues.isDarkModeEnabled()`, `ConfigValues.getMaxRetries()`, etc.
 * - ProGuard/R8 `-assumevalues` rules for local flags (enabling dead-code elimination in release builds)
 */
public open class FeaturedExtension {
    /** Container for flags resolved entirely on-device (local overrides). */
    public val localFlags: FlagContainer = FlagContainer()

    /** Container for flags controlled by a remote config service. */
    public val remoteFlags: FlagContainer = FlagContainer()

    /** Configures local feature flags. */
    public fun localFlags(configure: FlagContainer.() -> Unit): Unit = localFlags.configure()

    /** Configures remote feature flags. */
    public fun remoteFlags(configure: FlagContainer.() -> Unit): Unit = remoteFlags.configure()
}
