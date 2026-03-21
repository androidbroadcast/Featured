package dev.androidbroadcast.featured.registry

import dev.androidbroadcast.featured.ConfigParam

/**
 * Central registry that collects all [ConfigParam] instances across feature modules.
 * Powers debug UI auto-discovery of available feature flags.
 *
 * Thread-safe: registration and retrieval use platform-specific synchronization
 * (a lock on JVM/Android, CAS-based updates on Native/iOS).
 */
public object FlagRegistry {
    private val delegate = FlagRegistryDelegate()

    /**
     * Registers a [ConfigParam] with the registry.
     * Duplicate registrations (same param by key equality) are silently ignored.
     */
    public fun register(param: ConfigParam<*>) {
        delegate.register(param)
    }

    /**
     * Returns an immutable snapshot of all currently registered [ConfigParam] instances.
     */
    public fun all(): List<ConfigParam<*>> = delegate.all()

    /**
     * Clears all registered params. Intended for use in tests only.
     */
    internal fun reset() {
        delegate.reset()
    }
}
