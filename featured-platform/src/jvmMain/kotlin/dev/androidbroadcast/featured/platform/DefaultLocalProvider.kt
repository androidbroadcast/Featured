package dev.androidbroadcast.featured.platform

import dev.androidbroadcast.featured.InMemoryConfigValueProvider
import dev.androidbroadcast.featured.LocalConfigValueProvider

/**
 * Returns an [InMemoryConfigValueProvider] on JVM.
 *
 * A persistent JVM provider (Java Preferences-backed) is tracked in issue #66.
 * Until that module ships, this returns an in-memory provider so integrators can
 * adopt the `defaultLocalProvider()` call site today and benefit from persistence
 * automatically once #66 lands.
 */
public actual fun defaultLocalProvider(): LocalConfigValueProvider = InMemoryConfigValueProvider()
