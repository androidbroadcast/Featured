package dev.androidbroadcast.featured.sharedpreferences

import android.content.SharedPreferences
import dev.androidbroadcast.featured.TypeConverter

internal class StringValueSaver : ValueSaver<String> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: String,
    ) {
        editor.putString(key, value)
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): String? = pref.getString(key, null)
}

internal class IntValueSaver : ValueSaver<Int> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: Int,
    ) {
        editor.putInt(key, value)
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): Int? {
        if (key !in pref) return null
        return pref.getInt(key, 0)
    }
}

internal class BooleanValueSaver : ValueSaver<Boolean> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: Boolean,
    ) {
        editor.putBoolean(key, value)
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): Boolean? {
        if (key !in pref) return null
        return pref.getBoolean(key, false)
    }
}

internal class FloatValueSaver : ValueSaver<Float> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: Float,
    ) {
        editor.putFloat(key, value)
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): Float? {
        if (key !in pref) return null
        return pref.getFloat(key, 0f)
    }
}

internal class LongValueSaver : ValueSaver<Long> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: Long,
    ) {
        editor.putLong(key, value)
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): Long? {
        if (key !in pref) return null
        return pref.getLong(key, 0L)
    }
}

internal class DoubleValueSaver : ValueSaver<Double> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: Double,
    ) {
        editor.putLong(key, value.toRawBits())
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): Double? {
        if (key !in pref) return null
        return Double.fromBits(pref.getLong(key, 0L))
    }
}

/**
 * A [ValueSaver] that serializes values of type [T] to/from [String] using a [TypeConverter].
 *
 * Used internally by [SharedPreferencesProviderConfig.registerConverter] to support custom
 * types (e.g. enums) that do not have a native SharedPreferences storage type.
 */
internal class TypeConverterValueSaver<T : Any>(
    private val converter: TypeConverter<T>,
) : ValueSaver<T> {
    override fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: T,
    ) {
        editor.putString(key, converter.toString(value))
    }

    override fun read(
        pref: SharedPreferences,
        key: String,
    ): T? {
        val raw = pref.getString(key, null) ?: return null
        return converter.fromString(raw)
    }
}
