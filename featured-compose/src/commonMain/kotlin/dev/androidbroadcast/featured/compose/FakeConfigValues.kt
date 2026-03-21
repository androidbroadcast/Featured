package dev.androidbroadcast.featured.compose

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A builder scope used to configure overrides for [fakeConfigValues].
 */
public class FakeConfigValuesScope {
    internal val overrides: MutableMap<String, Any> = mutableMapOf()

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
    return ConfigValues(localProvider = FakeLocalConfigValueProvider(scope.overrides))
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

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        val override = overrides[param.key] as? T
        return if (override != null) {
            flowOf(ConfigValue(override, ConfigValue.Source.LOCAL))
        } else {
            flowOf()
        }
    }
}
