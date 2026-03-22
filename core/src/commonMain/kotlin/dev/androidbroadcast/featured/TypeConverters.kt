package dev.androidbroadcast.featured

import kotlin.reflect.KClass

/**
 * A mutable, type-keyed registry of [TypeConverter] instances.
 *
 * Providers that serialize configuration values as strings (SharedPreferences, DataStore,
 * Firebase Remote Config) use this registry to convert custom types. Register converters
 * before the provider is used:
 *
 * ```kotlin
 * val converters = TypeConverters()
 * converters.put<Theme>(enumConverter())
 * ```
 *
 * Use [put] (inline, reified) as a convenience over [set] to avoid passing [KClass] explicitly.
 */
public class TypeConverters {
    private val converters = mutableMapOf<KClass<*>, TypeConverter<*>>()

    /**
     * Registers [converter] for [klass], replacing any previously registered converter.
     *
     * @param klass The [KClass] of the type this converter handles.
     * @param converter The [TypeConverter] to register.
     */
    public operator fun <T : Any> set(
        klass: KClass<T>,
        converter: TypeConverter<T>,
    ) {
        converters[klass] = converter
    }

    /**
     * Returns the [TypeConverter] registered for [klass], or `null` if none is registered.
     *
     * @param klass The [KClass] to look up.
     * @return The registered [TypeConverter], or `null`.
     */
    @Suppress("UNCHECKED_CAST")
    public operator fun <T : Any> get(klass: KClass<T>): TypeConverter<T>? = converters[klass] as TypeConverter<T>?

    /**
     * Returns `true` if a converter is registered for [klass].
     */
    public operator fun contains(klass: KClass<*>): Boolean = klass in converters
}

/**
 * Registers [converter] for the reified type [T], replacing any previously registered converter.
 *
 * Inline convenience wrapper around [TypeConverters.set]:
 * ```kotlin
 * converters.put<Theme>(enumConverter())
 * ```
 *
 * @param T The non-null type this converter handles.
 * @param converter The [TypeConverter] to register.
 */
public inline fun <reified T : Any> TypeConverters.put(converter: TypeConverter<T>) {
    set(T::class, converter)
}

/**
 * Returns the [TypeConverter] registered for the reified type [T], or `null` if none.
 *
 * @param T The non-null type to look up.
 * @return The registered [TypeConverter], or `null`.
 */
public inline fun <reified T : Any> TypeConverters.get(): TypeConverter<T>? = get(T::class)
