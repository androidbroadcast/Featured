package dev.androidbroadcast.featured.javaprefs

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.TypeConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

/**
 * A [LocalConfigValueProvider] that persists configuration values using [java.util.prefs.Preferences].
 *
 * Storage is OS-specific: registry on Windows, plist on macOS, `~/.java` on Linux.
 *
 * @param node The [Preferences] node to use for storage. Defaults to `featured` under the user root.
 * @param context Additional [CoroutineContext] elements merged with [Dispatchers.IO].
 */
public class JavaPreferencesConfigValueProvider(
    private val node: Preferences = Preferences.userRoot().node("featured"),
    context: CoroutineContext = EmptyCoroutineContext,
) : LocalConfigValueProvider {
    private val coroutineContext: CoroutineContext = Dispatchers.IO + context
    private val changedKeysFlow = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)

    @Suppress("UNCHECKED_CAST")
    private val readers: Map<KClass<*>, (Preferences, String) -> Any?> =
        mapOf(
            String::class to { prefs, key ->
                prefs.get(key, null)
            },
            Int::class to { prefs, key ->
                if (prefs.get(key, null) == null) null else prefs.getInt(key, 0)
            },
            Boolean::class to { prefs, key ->
                if (prefs.get(key, null) == null) null else prefs.getBoolean(key, false)
            },
            Float::class to { prefs, key ->
                if (prefs.get(key, null) == null) null else prefs.getFloat(key, 0f)
            },
            Long::class to { prefs, key ->
                if (prefs.get(key, null) == null) null else prefs.getLong(key, 0L)
            },
            Double::class to { prefs, key ->
                if (prefs.get(key, null) == null) null else prefs.getDouble(key, 0.0)
            },
        )

    private val writers: Map<KClass<*>, (Preferences, String, Any) -> Unit> =
        mapOf(
            String::class to { prefs, key, value -> prefs.put(key, value as String) },
            Int::class to { prefs, key, value -> prefs.putInt(key, value as Int) },
            Boolean::class to { prefs, key, value -> prefs.putBoolean(key, value as Boolean) },
            Float::class to { prefs, key, value -> prefs.putFloat(key, value as Float) },
            Long::class to { prefs, key, value -> prefs.putLong(key, value as Long) },
            Double::class to { prefs, key, value -> prefs.putDouble(key, value as Double) },
        )

    private val converters = mutableMapOf<KClass<*>, TypeConverter<*>>()

    /**
     * Registers a [TypeConverter] for a custom type [T], enabling get/set/observe for that type.
     */
    public fun <T : Any> registerConverter(
        klass: KClass<T>,
        converter: TypeConverter<T>,
    ) {
        converters[klass] = converter
    }

    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
        withContext(coroutineContext) {
            readValue(param)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> readValue(param: ConfigParam<T>): ConfigValue<T>? {
        val klass = param.valueType
        val converter = converters[klass]
        if (converter != null) {
            val raw = node.get(param.key, null) ?: return null
            val value = (converter as TypeConverter<T>).fromString(raw)
            return ConfigValue(value, ConfigValue.Source.LOCAL)
        }
        val reader =
            readers[klass]
                ?: throw IllegalArgumentException("Unsupported type: $klass. Register a TypeConverter for custom types.")
        val value = (reader(node, param.key) as T?) ?: return null
        return ConfigValue(value, ConfigValue.Source.LOCAL)
    }

    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ): Unit =
        withContext(coroutineContext) {
            writeValue(param, value)
            node.flush()
            changedKeysFlow.tryEmit(param.key)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> writeValue(
        param: ConfigParam<T>,
        value: T,
    ) {
        val klass = param.valueType
        val converter = converters[klass]
        if (converter != null) {
            node.put(param.key, (converter as TypeConverter<T>).toString(value))
            return
        }
        val writer =
            writers[klass]
                ?: throw IllegalArgumentException("Unsupported type: $klass. Register a TypeConverter for custom types.")
        writer(node, param.key, value)
    }

    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>): Unit =
        withContext(coroutineContext) {
            node.remove(param.key)
            node.flush()
            changedKeysFlow.tryEmit(param.key)
        }

    override suspend fun clear(): Unit =
        withContext(coroutineContext) {
            node.clear()
            node.flush()
        }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        flow<ConfigValue<T>?> {
            emit(get(param))
            emitAll(
                changedKeysFlow
                    .filter { it == param.key }
                    .map { get(param) },
            )
        }.filterNotNull()
            .distinctUntilChanged()
}

/**
 * Registers a [TypeConverter] for a custom type [T] using a reified type parameter.
 */
public inline fun <reified T : Any> JavaPreferencesConfigValueProvider.registerConverter(converter: TypeConverter<T>) {
    registerConverter(T::class, converter)
}
