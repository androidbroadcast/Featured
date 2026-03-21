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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A [LocalConfigValueProvider] backed by AndroidX [DataStore] with [Preferences].
 *
 * Values are persisted across process restarts using the Preferences DataStore. Only the
 * following types are supported: [String], [Int], [Long], [Float], [Double], [Boolean].
 * Attempting to read or write an unsupported type throws [IllegalArgumentException].
 *
 * All write operations ([set], [resetOverride]) trigger a DataStore update, which in turn
 * causes any active [observe] flow to re-emit.
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
    /**
     * Returns the persisted value for [param], or `null` if it has not been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        val preferences = dataStore.data.first()
        val key = createPreferencesKey(param)

        return when (val value = preferences[key]) {
            null -> {
                null
            }

            else -> {
                ConfigValue(
                    value = value,
                    source = ConfigValue.Source.LOCAL,
                )
            }
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
        dataStore.edit { preferences ->
            preferences[createPreferencesKey(param)] = value
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
        dataStore.edit { preferences ->
            preferences.remove(createPreferencesKey(param))
        }
    }

    /**
     * Returns a [Flow] that emits a [ConfigValue] for [param] on every DataStore update.
     *
     * The flow emits immediately with the current persisted value (or [ConfigParam.defaultValue]
     * when no override is set) and then continues to emit on every subsequent change.
     *
     * @param param The configuration parameter to observe.
     * @return A [Flow] backed by the DataStore; completes when the collector's scope is cancelled.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        dataStore.data.map { preferences ->
            val key = createPreferencesKey(param)

            preferences[key]?.let { value ->
                ConfigValue(
                    value = value,
                    source = ConfigValue.Source.LOCAL,
                )
            } ?: ConfigValue(
                value = param.defaultValue,
                source = ConfigValue.Source.DEFAULT,
            )
        }

    public companion object {
        /**
         * Stable identifier for this provider; may be used for logging or dependency injection.
         */
        public const val ID: String = "dev.androidbroadcast.featured.datastore"
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> createPreferencesKey(param: ConfigParam<T>): Preferences.Key<T> =
    when (param.valueType) {
        String::class -> stringPreferencesKey(param.key)
        Int::class -> intPreferencesKey(param.key)
        Long::class -> longPreferencesKey(param.key)
        Float::class -> floatPreferencesKey(param.key)
        Double::class -> doublePreferencesKey(param.key)
        Boolean::class -> booleanPreferencesKey(param.key)
        else -> throw IllegalArgumentException("Unsupported type: ${param.valueType}")
    } as Preferences.Key<T>
