package dev.androidbroadcast.featured.platform

import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.nsuserdefaults.NSUserDefaultsConfigValueProvider

/**
 * Returns an [NSUserDefaultsConfigValueProvider] backed by the standard iOS user defaults.
 *
 * Values are persisted in `NSUserDefaults.standardUserDefaults` and survive app restarts.
 */
public actual fun defaultLocalProvider(): LocalConfigValueProvider = NSUserDefaultsConfigValueProvider()
