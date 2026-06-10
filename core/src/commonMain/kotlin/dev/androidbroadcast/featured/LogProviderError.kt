package dev.androidbroadcast.featured

/**
 * Logs a provider error to the platform-appropriate log output.
 *
 * Called by [ConfigValues] when a provider throws during [ConfigValues.getValue] or
 * [ConfigValues.observe]. Platform implementations:
 * - **Android** — `android.util.Log.w`
 * - **iOS** — `NSLog`
 * - **JVM** — `System.err`
 */
internal expect fun logProviderError(e: Throwable)
