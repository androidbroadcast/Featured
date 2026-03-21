package dev.androidbroadcast.featured.registry

import dev.androidbroadcast.featured.ConfigParam

internal expect class FlagRegistryDelegate() {
    fun register(param: ConfigParam<*>)

    fun all(): List<ConfigParam<*>>

    fun reset()
}
