package dev.androidbroadcast.featured.platform

import dev.androidbroadcast.featured.LocalConfigValueProvider

/**
 * Returns the platform-appropriate persistent [LocalConfigValueProvider].
 *
 * | Platform | Implementation |
 * |----------|----------------|
 * | iOS      | [dev.androidbroadcast.featured.nsuserdefaults.NSUserDefaultsConfigValueProvider] |
 * | JVM      | [dev.androidbroadcast.featured.InMemoryConfigValueProvider] |
 * | Android  | [dev.androidbroadcast.featured.InMemoryConfigValueProvider] (in-memory fallback) |
 *
 * **Android note:** For persistent storage on Android, prefer the overload that accepts a
 * `Context`:
 * ```kotlin
 * val provider = defaultLocalProvider(context) // returns DataStoreConfigValueProvider
 * ```
 *
 * **Usage (all platforms):**
 * ```kotlin
 * val configValues = ConfigValues(localProvider = defaultLocalProvider())
 * ```
 */
public expect fun defaultLocalProvider(): LocalConfigValueProvider
