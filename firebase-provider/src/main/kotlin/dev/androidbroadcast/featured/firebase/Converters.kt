package dev.androidbroadcast.featured.firebase

import kotlin.reflect.KClass

/**
 * A mutable, type-keyed registry of [Converter] instances used by [FirebaseConfigValueProvider].
 *
 * Built-in converters for [String], [Boolean], [Int], [Long], [Double], and [Float] are
 * pre-registered by [FirebaseConfigValueProvider]. Add custom converters via [put] or [set]:
 * ```kotlin
 * provider.converters[MyEnum::class] = Converter { MyEnum.fromString(it.asString()) }
 * // or using the inline helper:
 * provider.converters.put<MyEnum>(Converter { MyEnum.fromString(it.asString()) })
 * ```
 */
public class Converters internal constructor() {
    private val converters = mutableMapOf<KClass<*>, Converter<*>>()

    /**
     * Registers [converter] for [klass], replacing any previously registered converter.
     *
     * @param klass The [KClass] of the type this converter handles.
     * @param converter The [Converter] to register.
     */
    public operator fun <T : Any> set(
        klass: KClass<T>,
        converter: Converter<T>,
    ) {
        converters[klass] = converter
    }

    /**
     * Returns the [Converter] registered for [klass], or `null` if none is registered.
     *
     * @param klass The [KClass] to look up.
     * @return The registered [Converter], or `null`.
     */
    @Suppress("UNCHECKED_CAST")
    public operator fun <T : Any> get(klass: KClass<T>): Converter<T>? = converters[klass] as Converter<T>?

    /**
     * Returns the [Converter] registered for [klass].
     *
     * @param klass The [KClass] to look up.
     * @return The registered [Converter].
     * @throws NoSuchElementException if no converter is registered for [klass].
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getValue(klass: KClass<T>): Converter<T> = converters.getValue(klass) as Converter<T>

    /**
     * Returns `true` if a converter is registered for [klass].
     *
     * @param klass The [KClass] to check.
     */
    public operator fun contains(klass: KClass<*>): Boolean = klass in converters
}

/**
 * Registers [converter] for the reified type [T], replacing any previously registered converter.
 *
 * Inline convenience wrapper around [Converters.set] that avoids passing an explicit [KClass]:
 * ```kotlin
 * converters.put<Theme>(Converter { Theme.fromKey(it.asString()) })
 * ```
 *
 * @param T The non-null type this converter handles.
 * @param converter The [Converter] to register.
 */
public inline fun <reified T : Any> Converters.put(converter: Converter<T>) {
    set(T::class, converter)
}
