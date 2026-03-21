package dev.androidbroadcast.featured

/**
 * Marks a [ConfigParam] property as a local feature flag.
 *
 * Properties annotated with `@LocalFlag` are scanned by the Featured Gradle plugin
 * to produce a structured list of all local flags across modules. This list is
 * used by downstream code-generation tasks (e.g. a developer-settings screen).
 *
 * Usage:
 * ```kotlin
 * @LocalFlag
 * val darkMode = ConfigParam("dark_mode", false)
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
public annotation class LocalFlag
