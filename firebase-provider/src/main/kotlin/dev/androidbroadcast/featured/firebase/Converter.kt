package dev.androidbroadcast.featured.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlin.ranges.contains

public fun interface Converter<T : Any> {

    public fun convert(value: FirebaseRemoteConfigValue): T
}

internal class IntConverter : Converter<Int> {

    override fun convert(value: FirebaseRemoteConfigValue): Int {
        return value.asLong()
            .also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Value outside of Int range" } }
            .toInt()
    }
}

internal class FloatConverter : Converter<Float> {

    override fun convert(value: FirebaseRemoteConfigValue): Float {
        return value.asDouble()
            .also { require(it in Float.MIN_VALUE..Float.MAX_VALUE) { "Value outside of Float range" } }
            .toFloat()
    }
}