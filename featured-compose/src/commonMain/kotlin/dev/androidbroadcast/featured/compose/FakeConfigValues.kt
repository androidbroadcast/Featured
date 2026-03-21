package dev.androidbroadcast.featured.compose

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A builder scope used to configure overrides for [fakeConfigValues].
 *
 * Use [set] to define per-param overrides. The scope is opaque — only [set] is part of the
 * public API.
 */
public class FakeConfigValuesScope internal constructor() {
    private val overrides: MutableMap<String, Any> = mutableMapOf()

    /**
     * Sets an override value for the given [param].
     * The overridden value will be returned instead of [ConfigParam.defaultValue].
     */
    public fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        overrides[param.key] = value
    }

    internal fun buildOverrides(): Map<String, Any> = overrides.toMap()
}

/**
 * Creates a [ConfigValues] suitable for use in Compose Previews.
 *
 * Override specific params via the [block] lambda; all other params return their [ConfigParam.defaultValue].
 *
 * Example:
 * ```kotlin
 * @Preview
 * @Composable
 * fun MyPreview() {
 *     CompositionLocalProvider(
 *         LocalConfigValues provides fakeConfigValues { set(NewCheckoutFlag, true) }
 *     ) {
 *         MyScreen()
 *     }
 * }
 * ```
 */
public fun fakeConfigValues(block: FakeConfigValuesScope.() -> Unit = {}): ConfigValues {
    val scope = FakeConfigValuesScope().apply(block)
    return ConfigValues(localProvider = FakeLocalConfigValueProvider(scope.buildOverrides()))
}

private class FakeLocalConfigValueProvider(
    private val overrides: Map<String, Any>,
) : LocalConfigValueProvider {
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
        (overrides[param.key] as? T)?.let { value ->
            ConfigValue(value, ConfigValue.Source.LOCAL)
        }

    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        // no-op: fake provider does not support runtime mutations
    }

    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        // no-op: fake provider does not support runtime mutations
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        val override = overrides[param.key] as? T
        // Emit only when an override exists. For unset params, ConfigValues.observe() falls back
        // to the defaultValue via getValue(), so returning an empty flow is correct here.
        return if (override != null) {
            flowOf(ConfigValue(override, ConfigValue.Source.LOCAL))
        } else {
            flowOf()
        }
    }
}
