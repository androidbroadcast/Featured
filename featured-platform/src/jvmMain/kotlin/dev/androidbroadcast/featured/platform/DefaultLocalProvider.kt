package dev.androidbroadcast.featured.platform

import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.javaprefs.JavaPreferencesConfigValueProvider

/**
 * Returns a [JavaPreferencesConfigValueProvider] on JVM (persists via `java.util.prefs.Preferences`).
 */
public actual fun defaultLocalProvider(): LocalConfigValueProvider = JavaPreferencesConfigValueProvider()
