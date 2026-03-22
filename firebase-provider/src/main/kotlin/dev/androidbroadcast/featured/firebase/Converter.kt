package dev.androidbroadcast.featured.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlin.ranges.contains

/**
 * Converts a raw [FirebaseRemoteConfigValue] to a typed value [T].
 *
 * Implement this functional interface to add support for custom types in
 * [FirebaseConfigValueProvider.converters]:
 * ```kotlin
 * provider.converters.put<Theme>(Converter { Theme.fromKey(it.asString()) })
 * ```
 *
 * @param T The non-null target type produced by [convert].
 */
public fun interface Converter<T : Any> {
    /**
     * Converts [value] from Firebase to a typed [T].
     *
     * @param value The raw Firebase Remote Config value.
     * @return The converted value.
     * @throws IllegalArgumentException if the raw value cannot be converted to [T].
     */
    public fun convert(value: FirebaseRemoteConfigValue): T
}

internal class IntConverter : Converter<Int> {
    override fun convert(value: FirebaseRemoteConfigValue): Int =
        value
            .asLong()
            .also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Value outside of Int range" } }
            .toInt()
}

internal class FloatConverter : Converter<Float> {
    override fun convert(value: FirebaseRemoteConfigValue): Float =
        value
            .asDouble()
            .also { require(it.isFinite()) { "Value must be finite (not NaN or Infinity)" } }
            .toFloat()
}
