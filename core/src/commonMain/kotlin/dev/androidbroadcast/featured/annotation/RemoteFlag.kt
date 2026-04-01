package dev.androidbroadcast.featured

/**
 * Marks a [ConfigParam] property as a remote feature flag.
 *
 * Properties annotated with `@RemoteFlag` are intended to be controlled by a remote
 * configuration provider (e.g. Firebase Remote Config). The Featured Gradle plugin
 * and Detekt rules use this annotation to enforce correct provider setup.
 *
 * Usage:
 * ```kotlin
 * @RemoteFlag
 * val newCheckout = ConfigParam("new_checkout", false)
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
public annotation class RemoteFlag
