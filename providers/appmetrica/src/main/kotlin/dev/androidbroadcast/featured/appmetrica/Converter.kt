package dev.androidbroadcast.featured.appmetrica

import com.yandex.varioqub.config.model.ConfigValue

/**
 * Converts a raw Varioqub [ConfigValue] to a typed value [T].
 *
 * Implement this functional interface to add support for custom types in
 * [AppMetricaConfigValueProvider.converters]:
 * ```kotlin
 * provider.converters.put<Theme>(Converter { Theme.valueOf(it.asString()) })
 * ```
 *
 * This is not the core `TypeConverter` used by local providers.
 *
 * @param T The non-null target type produced by [convert].
 */
public fun interface Converter<T : Any> {
    /**
     * Converts [value] from Varioqub to a typed [T].
     *
     * @param value The raw Varioqub config value.
     * @return The converted value.
     * @throws IllegalArgumentException if the raw value cannot be converted to [T].
     */
    public fun convert(value: ConfigValue): T
}

internal class IntConverter : Converter<Int> {
    override fun convert(value: ConfigValue): Int =
        value
            .asLong()
            .also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Value outside of Int range" } }
            .toInt()
}

internal class FloatConverter : Converter<Float> {
    override fun convert(value: ConfigValue): Float =
        value
            .asDouble()
            .also { require(it.isFinite()) { "Value must be finite (not NaN or Infinity)" } }
            .toFloat()
}
