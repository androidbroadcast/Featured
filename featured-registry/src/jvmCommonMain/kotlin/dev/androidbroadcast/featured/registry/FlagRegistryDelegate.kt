package dev.androidbroadcast.featured.registry

import dev.androidbroadcast.featured.ConfigParam
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual class FlagRegistryDelegate actual constructor() {
    private val lock = ReentrantLock()
    private val params: LinkedHashSet<ConfigParam<*>> = LinkedHashSet()

    actual fun register(param: ConfigParam<*>) {
        lock.withLock { params.add(param) }
    }

    actual fun all(): List<ConfigParam<*>> =
        lock.withLock { params.toList() }

    actual fun reset() {
        lock.withLock { params.clear() }
    }
}
