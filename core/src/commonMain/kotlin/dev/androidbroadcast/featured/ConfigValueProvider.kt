package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow

/**
 * An interface for providing configuration values from various sources.
 * It allows retrieval of configuration values based on parameters and supports
 * both local and remote configurations.
 *
 * Implementations of this interface can provide values from different sources,
 * such as local storage, remote servers, or a combination of both.
 */
public sealed interface ConfigValueProvider {

    /**
     * Retrieves the configuration value for the given parameter.
     * If the value is not available, returns null.
     *
     * @param param The configuration parameter to retrieve the value for.
     *
     * @return The value for the specified [param], or null if not available.
     */
    public suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>?
}

public interface RemoteConfigValueProvider : ConfigValueProvider {

    /**
     * Fetches the latest configuration values from the remote source and apply them.
     * This method should be called to ensure that the latest values are available.
     * Recommended to do in on start of user's app session (not equals app start).
     *
     * @param activate If true, the fetched values will be activated immediately.
     *                 Some providers can't support this and will ignore this parameter.
     */
    public suspend fun fetch(activate: Boolean = true)
}

public interface LocalConfigValueProvider : ConfigValueProvider {

    /**
     * Sets the configuration value for the given parameter.
     * This method should be used to update local values,
     * which may not be reflected in remote sources.
     *
     * @param param The configuration parameter to set the value for.
     * @param value The value to set for the specified parameter.
     */
    public suspend fun <T : Any> set(param: ConfigParam<T>, value: T)

    /**
     * Observes changes to the configuration value for the given parameter.
     * It emits the latest value immediately and then continues to emit updates
     * whenever the value changes locally.
     *
     * @param param The configuration parameter to observe.
     *
     * @return A [Flow] of configuration values for the specified parameter.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>>
}
