@file:Suppress("unused")

package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * A class that provides access to configuration values from both local and remote sources.
 * Local values are typically used for user-specific overrides and have higher priority,
 * while remote values are fetched from a remote configuration service.
 *
 * If both local and remote values are available, the local value will be returned.
 * If neither is available, the default value from the parameter will be returned.
 */
public class ConfigValues(
    private val localProvider: LocalConfigValueProvider? = null,
    private val remoteProvider: RemoteConfigValueProvider? = null,
) {
    init {
        require(localProvider != null || remoteProvider != null) {
            "At least one provider (local or remote) must be provided."
        }
    }

    public suspend fun <T : Any> getValue(
        param: ConfigParam<T>,
    ): ConfigValue<T> {
        return localProvider?.get(param)
            ?: remoteProvider?.get(param)
            ?: ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
    }

    /**
     * Overrides the configuration value for the given parameter with a local value.
     * This method is used to set a user-specific value that will take precedence over
     * any remote value for the specified parameter.
     *
     * Usually used for testing purposes or to allow users to customize.
     *
     * @param param The configuration parameter to override.
     */
    public suspend fun <T : Any> override(
        param: ConfigParam<T>,
        value: T,
    ) {
        localProvider?.set(param, value)
    }

    public suspend fun fetch() {
        remoteProvider?.fetch(true)
    }

    /**
     * Observes changes to the configuration value for the given parameter.
     * It emits the latest value immediately and then continues to emit updates
     * whenever the value changes locally.
     *
     * @param param The configuration parameter to observe.
     * @return A flow of configuration values for the specified parameter.
     */
    public fun <T : Any> observe(
        param: ConfigParam<T>,
    ): Flow<ConfigValue<T>> {
        return flow<ConfigValue<T>> {
            emit(getValue(param)) // get latest value
            localProvider?.observe(param)?.collect { emit(it) } // observe changes
        }.distinctUntilChanged()
    }
}
