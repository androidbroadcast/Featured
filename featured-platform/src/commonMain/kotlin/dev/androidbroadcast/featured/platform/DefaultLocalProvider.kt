package dev.androidbroadcast.featured.platform

import dev.androidbroadcast.featured.LocalConfigValueProvider

/**
 * Returns the platform-appropriate persistent [LocalConfigValueProvider].
 *
 * | Platform | Implementation |
 * |----------|----------------|
 * | iOS      | [dev.androidbroadcast.featured.nsuserdefaults.NSUserDefaultsConfigValueProvider] |
 * | JVM      | [dev.androidbroadcast.featured.InMemoryConfigValueProvider] (persistent provider pending #66) |
 * | Android  | [dev.androidbroadcast.featured.InMemoryConfigValueProvider] (non-persistent, deprecated) |
 *
 * **Android note:** On Android this overload is deprecated and returns a non-persistent
 * in-memory provider. Use the platform-specific overload that accepts a `Context` to get
 * a DataStore-backed persistent provider:
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
