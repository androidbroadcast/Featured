package dev.androidbroadcast.featured.firebase

import kotlin.reflect.KClass

public class Converters internal constructor() {

    private val converters = mutableMapOf<KClass<*>, Converter<*>>()

    public operator fun <T : Any> set(klass: KClass<T>, converter: Converter<T>) {
        converters[klass] = converter
    }

    @Suppress("UNCHECKED_CAST")
    public operator fun <T : Any> get(klass: KClass<T>): Converter<T>? {
        return converters[klass] as Converter<T>?
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getValue(klass: KClass<T>): Converter<T> {
        return converters.getValue(klass) as Converter<T>
    }

    public operator fun contains(klass: KClass<*>): Boolean = klass in converters
}

public inline fun <reified T : Any> Converters.put(
    converter: Converter<T>,
) {
    set(T::class, converter)
}