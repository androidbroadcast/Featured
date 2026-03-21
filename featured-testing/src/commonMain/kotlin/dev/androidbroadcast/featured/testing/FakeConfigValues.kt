package dev.androidbroadcast.featured.testing

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.InMemoryConfigValueProvider

/**
 * A builder scope used to configure initial overrides for [fakeConfigValues].
 *
 * Use [set] to define per-param overrides. The scope is opaque — only [set] is part of the
 * public API.
 */
public class FakeConfigValuesScope internal constructor() {
    private val overrides: MutableList<suspend InMemoryConfigValueProvider.() -> Unit> =
        mutableListOf()

    /**
     * Sets an initial override value for the given [param].
     *
     * The overridden value will be returned instead of [ConfigParam.defaultValue].
     * Values can be changed mid-test via [ConfigValues.override].
     */
    public fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        overrides.add { set(param, value) }
    }

    internal suspend fun applyTo(provider: InMemoryConfigValueProvider) {
        overrides.forEach { action -> provider.action() }
    }
}

/**
 * Creates a [ConfigValues] suitable for use in unit tests and Compose Previews.
 *
 * Initial values for specific params are set via [block]. Unset params fall back to
 * [ConfigParam.defaultValue]. Values can be changed mid-test using [ConfigValues.override],
 * which triggers reactive [ConfigValues.observe] updates.
 *
 * ```kotlin
 * val configValues = fakeConfigValues {
 *     set(NewCheckoutFlag, true)
 *     set(DarkThemeFlag, false)
 * }
 *
 * // Read initial value
 * val value = configValues.getValue(NewCheckoutFlag) // true
 *
 * // Simulate remote change mid-test
 * configValues.override(NewCheckoutFlag, false)
 * ```
 */
public suspend fun fakeConfigValues(block: FakeConfigValuesScope.() -> Unit = {}): ConfigValues {
    val scope = FakeConfigValuesScope().apply(block)
    val provider = InMemoryConfigValueProvider()
    scope.applyTo(provider)
    return ConfigValues(localProvider = provider)
}
