package dev.androidbroadcast.featured.sharedpreferences

import kotlin.reflect.KClass

internal class ValueSavers {

    private val savers = mutableMapOf<KClass<*>, ValueSaver<*>>()

    operator fun <T : Any> set(type: KClass<T>, saver: ValueSaver<T>) {
        savers[type] = saver
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(type: KClass<T>): ValueSaver<T>? {
        return savers[type] as? ValueSaver<T>
    }
}

internal inline fun <reified T : Any> ValueSavers.put(saver: ValueSaver<T>) {
    set(T::class, saver)
}
