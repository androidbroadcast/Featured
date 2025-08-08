package dev.androidbroadcast.featured.sharedpreferences

import android.content.SharedPreferences
import kotlin.reflect.KClass

internal class ValueSavers {

    private val savers = mutableMapOf<KClass<*>, ValueSaver<*>>()

    operator fun <T: Any> set(type: KClass<T>, saver: ValueSaver<T>) {
        savers[type] = saver
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Any> get(type: KClass<T>): ValueSaver<T>? {
        return savers[type] as? ValueSaver<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> getValue(type: KClass<T>): ValueSaver<T>? {
        return savers.getValue(type) as ValueSaver<T>?
    }
}

internal inline fun <reified T: Any> ValueSavers.put(saver: ValueSaver<T>) {
    set(T::class, saver)
}

internal interface ValueSaver<T : Any> {

    fun write(editor: SharedPreferences.Editor, key: String, value: T)

    fun read(pref: SharedPreferences, key: String): T?
}

internal class StringValueSaver : ValueSaver<String> {

    override fun write(editor: SharedPreferences.Editor, key: String, value: String) {
        editor.putString(key, value)
    }

    override fun read(pref: SharedPreferences, key: String): String? = pref.getString(key, null)
}

internal class IntValueSaver : ValueSaver<Int> {

    override fun write(editor: SharedPreferences.Editor, key: String, value: Int) {
        editor.putInt(key, value)
    }

    override fun read(pref: SharedPreferences, key: String): Int? {
        if (key !in pref) return null
        return pref.getInt(key, 0)
    }
}

internal class BooleanValueSaver : ValueSaver<Boolean> {

    override fun write(editor: SharedPreferences.Editor, key: String, value: Boolean) {
        editor.putBoolean(key, value)
    }

    override fun read(pref: SharedPreferences, key: String): Boolean? {
        if (key !in pref) return null
        return pref.getBoolean(key, false)
    }
}

internal class FloatValueSaver : ValueSaver<Float> {

    override fun write(editor: SharedPreferences.Editor, key: String, value: Float) {
        editor.putFloat(key, value)
    }

    override fun read(pref: SharedPreferences, key: String): Float? {
        if (key !in pref) return null
        return pref.getFloat(key, 0f)
    }
}

internal class LongValueSaver : ValueSaver<Long> {

    override fun write(editor: SharedPreferences.Editor, key: String, value: Long) {
        editor.putLong(key, value)
    }

    override fun read(pref: SharedPreferences, key: String): Long? {
        if (key !in pref) return null
        return pref.getLong(key, 0L)
    }
}

internal class DoubleValueSaver : ValueSaver<Double> {

    override fun write(editor: SharedPreferences.Editor, key: String, value: Double) {
        editor.putLong(key, value.toRawBits())
    }

    override fun read(pref: SharedPreferences, key: String): Double? {
        if (key !in pref) return null
        return Double.fromBits(pref.getLong(key, 0L))
    }
}