package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A centralized registry for configuration parameters. This registry holds all parameters that are
 * used across the application. It provides a way to manage configuration parameters in a
 * centralized manner.
 */
public object ConfigParamRegistry {

    private val _registeredParams = MutableStateFlow<Set<ConfigParam<*>>>(emptySet())
    public val registeredParams: StateFlow<Set<ConfigParam<*>>> = _registeredParams.asStateFlow()

    public fun <T : Any> register(param: ConfigParam<T>): ConfigParam<T> {
        _registeredParams.value += param
        return param
    }

    /**
     * Remote all registered params.
     * Used for testing purposes to reset the registry.
     */
    internal fun clear() {
        _registeredParams.value = emptySet()
    }

    public fun getAll(): Set<ConfigParam<*>> {
        return _registeredParams.value
    }
}
