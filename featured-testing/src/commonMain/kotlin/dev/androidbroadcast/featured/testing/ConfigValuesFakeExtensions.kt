package dev.androidbroadcast.featured.testing

import dev.androidbroadcast.featured.ConfigValues

/**
 * Creates a [ConfigValues] suitable for use in unit tests.
 *
 * This is a convenience alias for [fakeConfigValues] callable on the [ConfigValues] companion.
 *
 * ```kotlin
 * val configValues = ConfigValues.fake {
 *     set(NewCheckoutFlag, true)
 *     set(DarkThemeFlag, false)
 * }
 * ```
 *
 * @see fakeConfigValues
 */
public suspend fun ConfigValues.Companion.fake(
    block: FakeConfigValuesScope.() -> Unit = {},
): ConfigValues = fakeConfigValues(block)
