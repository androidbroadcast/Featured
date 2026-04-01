package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow

public interface LocalConfigValueProvider : ConfigValueProvider {
    /**
     * Sets the configuration value for the given parameter.
     * This method should be used to update local values,
     * which may not be reflected in remote sources.
     *
     * @param param The configuration parameter to set the value for.
     * @param value The value to set for the specified parameter.
     */
    public suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    )

    /**
     * Removes the locally overridden value for the given parameter, so the next
     * [get] call returns `null` and the effective value falls back to remote or default.
     *
     * @param param The configuration parameter whose local override should be cleared.
     */
    public suspend fun <T : Any> resetOverride(param: ConfigParam<T>)

    /**
     * Removes all locally overridden values, resetting the provider to an empty state.
     *
     * After this call, [get] returns `null` for every parameter that was previously
     * overridden, and [ConfigValues] falls back to the remote provider or
     * [ConfigParam.defaultValue].
     */
    public suspend fun clear()

    /**
     * Observes changes to the configuration value for the given parameter.
     * It emits the latest value immediately and then continues to emit updates
     * whenever the value changes locally.
     *
     * @param param The configuration parameter to observe.
     *
     * @return A [kotlinx.coroutines.flow.Flow] of configuration values for the specified parameter.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>>
}