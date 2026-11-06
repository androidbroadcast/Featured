package dev.androidbroadcast.featured.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.TypeConverter
import dev.androidbroadcast.featured.TypeConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

/**
 * A [LocalConfigValueProvider] backed by AndroidX [DataStore] with [Preferences].
 *
 * Values are persisted across process restarts using the Preferences DataStore.
 * Built-in types [String], [Int], [Long], [Float], [Double], and [Boolean] are supported
 * natively. Custom types (e.g. enums) are supported by registering a [TypeConverter] via
 * [registerConverter] before the first [get] or [set] call for that type.
 * Attempting to read or write a type with no built-in support and no registered converter
 * throws [IllegalArgumentException].
 *
 * All write operations ([set], [resetOverride]) trigger a DataStore update. [observe] re-emits
 * only when this param's slot changes, so a write to another scope's slot is not a signal.
 *
 * ```kotlin
 * val dataStore: DataStore<Preferences> = context.createDataStore("feature_flags")
 * val provider = DataStoreConfigValueProvider(dataStore)
 * val configValues = ConfigValues(localProvider = provider)
 * ```
 *
 * @param dataStore The [DataStore] instance used to read and write preference values.
 */
public class DataStoreConfigValueProvider(
    private val dataStore: DataStore<Preferences>,
) : LocalConfigValueProvider {
    private val typeConverters = TypeConverters()

    /**
     * Registers a [TypeConverter] for [klass], enabling [set] and [get] for custom types
     * (e.g. enums) that are serialized as strings in DataStore.
     *
     * Must be called before any [set] or [get] call for the corresponding type.
     * Prefer the inline overload [registerConverter] to avoid passing [KClass] explicitly.
     *
     * @param T The non-null type to register.
     * @param klass The [KClass] of the type.
     * @param converter The [TypeConverter] that serializes/deserializes [T] as a [String].
     */
    public fun <T : Any> registerConverter(
        klass: KClass<T>,
        converter: TypeConverter<T>,
    ) {
        typeConverters[klass] = converter
    }

    /**
     * Returns the persisted value for [param], or `null` if it has not been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        val converter = typeConverters.get(param.valueType)
        return if (converter != null) {
            getWithConverter(param, converter)
        } else {
            val preferences = dataStore.data.first()
            val key = createPreferencesKey(param)
            preferences[key]?.let { value ->
                ConfigValue(value = value, source = ConfigValue.Source.LOCAL)
            }
        }
    }

    private suspend fun <T : Any> getWithConverter(
        param: ConfigParam<T>,
        converter: TypeConverter<T>,
    ): ConfigValue<T>? {
        val preferences = dataStore.data.first()
        val key = stringPreferencesKey(param.localStorageKey())
        return preferences[key]?.let { raw ->
            ConfigValue(value = converter.fromString(raw), source = ConfigValue.Source.LOCAL)
        }
    }

    /**
     * Persists [value] as a local override for [param].
     *
     * Active [observe] flows for the same [param] will re-emit after the DataStore write
     * completes.
     *
     * @param param The configuration parameter to override.
     * @param value The value to persist.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        val converter = typeConverters.get(param.valueType)
        if (converter != null) {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(param.localStorageKey())] = converter.toString(value)
            }
        } else {
            dataStore.edit { preferences ->
                preferences[createPreferencesKey(param)] = value
            }
        }
    }

    /**
     * Removes the persisted override for [param].
     *
     * After this call, [get] returns `null` for [param] and [ConfigValues] falls back to
     * the remote provider or [ConfigParam.defaultValue].
     *
     * @param param The configuration parameter whose override should be cleared.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        val converter = typeConverters.get(param.valueType)
        if (converter != null) {
            dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey(param.localStorageKey()))
            }
        } else {
            dataStore.edit { preferences ->
                preferences.remove(createPreferencesKey(param))
            }
        }
    }

    /**
     * Removes all persisted overrides by clearing the entire DataStore preferences file.
     *
     * After this call, [get] returns `null` for every parameter that was previously set,
     * and [ConfigValues] falls back to the remote provider or [ConfigParam.defaultValue].
     * Active [observe] flows will re-emit after the DataStore write completes.
     */
    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    /**
     * Returns a [Flow] that emits a [ConfigValue] for [param] on every DataStore update.
     *
     * The flow emits immediately with the current persisted value (or [ConfigParam.defaultValue]
     * when no override is set) and then emits when this param's slot changes. A write that does
     * not change this slot, including a write to another scope's slot, is not a signal.
     *
     * @param param The configuration parameter to observe.
     * @return A [Flow] backed by the DataStore; completes when the collector's scope is cancelled.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        val converter = typeConverters.get(param.valueType)
        return if (converter != null) {
            dataStore.data
                .map { preferences ->
                    preferences[stringPreferencesKey(param.localStorageKey())]?.let { raw ->
                        ConfigValue(value = converter.fromString(raw), source = ConfigValue.Source.LOCAL)
                    } ?: ConfigValue(value = param.defaultValue, source = ConfigValue.Source.DEFAULT)
                }.distinctUntilChanged()
        } else {
            dataStore.data
                .map { preferences ->
                    val key = createPreferencesKey(param)
                    preferences[key]?.let { value ->
                        ConfigValue(value = value, source = ConfigValue.Source.LOCAL)
                    } ?: ConfigValue(value = param.defaultValue, source = ConfigValue.Source.DEFAULT)
                }.distinctUntilChanged()
        }
    }

    public companion object {
        /**
         * Stable identifier for this provider; may be used for logging or dependency injection.
         */
        public const val ID: String = "dev.androidbroadcast.featured.datastore"
    }
}

/**
 * Registers a [TypeConverter] for the reified type [T] on this [DataStoreConfigValueProvider],
 * enabling [DataStoreConfigValueProvider.set] and [DataStoreConfigValueProvider.get] for custom
 * types (e.g. enums) that are serialized as strings in DataStore.
 *
 * Inline convenience wrapper that avoids passing [kotlin.reflect.KClass] explicitly:
 * ```kotlin
 * provider.registerConverter(enumConverter<CheckoutVariant>())
 * ```
 *
 * @param T The non-null custom type to register.
 * @param converter The [TypeConverter] that serializes/deserializes [T] as a [String].
 */
public inline fun <reified T : Any> DataStoreConfigValueProvider.registerConverter(converter: TypeConverter<T>) {
    registerConverter(T::class, converter)
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> createPreferencesKey(param: ConfigParam<T>): Preferences.Key<T> =
    when (param.valueType) {
        String::class -> stringPreferencesKey(param.localStorageKey())
        Int::class -> intPreferencesKey(param.localStorageKey())
        Long::class -> longPreferencesKey(param.localStorageKey())
        Float::class -> floatPreferencesKey(param.localStorageKey())
        Double::class -> doublePreferencesKey(param.localStorageKey())
        Boolean::class -> booleanPreferencesKey(param.localStorageKey())
        else -> throw IllegalArgumentException("Unsupported type: ${param.valueType}")
    } as Preferences.Key<T>
