package dev.androidbroadcast.featured.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlin.ranges.contains

public fun interface Converter<T : Any> {
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
