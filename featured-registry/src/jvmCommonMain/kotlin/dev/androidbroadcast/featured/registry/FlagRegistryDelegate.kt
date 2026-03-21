package dev.androidbroadcast.featured.registry

import dev.androidbroadcast.featured.ConfigParam
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual class FlagRegistryDelegate actual constructor() {
    private val lock = ReentrantLock()

    // Keyed by ConfigParam.key to guarantee one entry per key across platforms.
    private val params: LinkedHashMap<String, ConfigParam<*>> = LinkedHashMap()

    actual fun register(param: ConfigParam<*>) {
        lock.withLock { params.putIfAbsent(param.key, param) }
    }

    actual fun all(): List<ConfigParam<*>> =
        lock.withLock { params.values.toList() }

    actual fun reset() {
        lock.withLock { params.clear() }
    }
}
