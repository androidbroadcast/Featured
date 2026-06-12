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
 *     generation {
 *         // Module-wide defaults for the generated code (all optional):
 *         packageName = "com.example.flags"        // default: dev.androidbroadcast.featured.generated
 *         visibility = FeaturedVisibility.INTERNAL // default: INTERNAL
 *     }
 *     localFlags {
 *         generation {
 *             className = "MyLocalFlags" // default: GeneratedLocalFlags<ModuleSuffix>
 *         }
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
 *
 * To fail the build on expired flags instead of just warning:
 * ```kotlin
 * featured {
 *     expiredFlagsMode = ExpiredFlagsMode.ERROR
 * }
 * ```
 */
public open class FeaturedExtension {
    /** Container for flags resolved entirely on-device (local overrides). */
    public val localFlags: FlagContainer = FlagContainer()

    /** Container for flags controlled by a remote config service. */
    public val remoteFlags: FlagContainer = FlagContainer()

    /**
     * Controls how [VerifyExpiredFlagsTask] reacts to flags whose `expiresAt` date is in the past.
     *
     * - [ExpiredFlagsMode.WARN] (default) — emits a Gradle warning per expired flag; build succeeds.
     * - [ExpiredFlagsMode.ERROR] — fails the build and lists all expired flags in the error message.
     *
     * Invalid `expiresAt` format strings are always reported as warnings and are never escalated,
     * regardless of this setting.
     *
     * Usage:
     * ```kotlin
     * featured {
     *     expiredFlagsMode = ExpiredFlagsMode.ERROR
     * }
     * ```
     */
    public var expiredFlagsMode: ExpiredFlagsMode = ExpiredFlagsMode.WARN

    /**
     * Module-wide defaults for the generated code. A value not overridden in a section's
     * `localFlags { generation { } }` / `remoteFlags { generation { } }` block falls back
     * to the value set here; unset values fall back to the built-in defaults.
     *
     * [GenerationSettings.className] is not supported at this level — there are two
     * generated objects, so a shared name would always collide.
     */
    public val generation: GenerationSettings = GenerationSettings()

    /** Configures local feature flags. */
    public fun localFlags(configure: FlagContainer.() -> Unit): Unit = localFlags.configure()

    /** Configures remote feature flags. */
    public fun remoteFlags(configure: FlagContainer.() -> Unit): Unit = remoteFlags.configure()

    /** Configures module-wide defaults for the generated code. */
    public fun generation(configure: GenerationSettings.() -> Unit): Unit = generation.configure()
}
