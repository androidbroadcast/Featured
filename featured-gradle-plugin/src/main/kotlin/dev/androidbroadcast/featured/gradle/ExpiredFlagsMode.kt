package dev.androidbroadcast.featured.gradle

/**
 * Controls how [VerifyExpiredFlagsTask] reacts when it detects feature flags whose
 * `expiresAt` date is in the past.
 *
 * Configure via the `featured { }` DSL extension:
 * ```kotlin
 * featured {
 *     expiredFlagsMode = ExpiredFlagsMode.ERROR
 * }
 * ```
 */
public enum class ExpiredFlagsMode {
    /**
     * Emit a Gradle build warning for each expired flag and continue the build.
     *
     * This is the default. The build succeeds regardless of how many flags are expired.
     */
    WARN,

    /**
     * Fail the build with a [org.gradle.api.GradleException] listing all expired flags
     * when at least one flag's `expiresAt` date is in the past.
     *
     * Invalid `expiresAt` format strings are always reported as warnings and are never
     * escalated to an error — only flags with a valid, past date trigger a build failure.
     */
    ERROR,
}
