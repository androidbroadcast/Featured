package dev.androidbroadcast.featured.registry

import dev.androidbroadcast.featured.ConfigParam
import kotlin.concurrent.AtomicReference

internal actual class FlagRegistryDelegate actual constructor() {
    // AtomicReference provides safe publication on Kotlin/Native new memory model.
    // Copy-on-write pattern ensures all() snapshots are consistent.
    private val paramsRef = AtomicReference<List<ConfigParam<*>>>(emptyList())

    actual fun register(param: ConfigParam<*>) {
        // Spin-loop CAS to add param if not already present (by equals/hashCode on key).
        while (true) {
            val current = paramsRef.value
            if (current.any { it.key == param.key }) return
            val next = current + param
            if (paramsRef.compareAndSet(current, next)) return
        }
    }

    actual fun all(): List<ConfigParam<*>> = paramsRef.value

    actual fun reset() {
        paramsRef.value = emptyList()
    }
}
