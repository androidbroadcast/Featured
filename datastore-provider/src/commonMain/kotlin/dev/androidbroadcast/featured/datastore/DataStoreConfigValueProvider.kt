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

public class DataStoreConfigValueProvider(
    private val dataStore: DataStore<Preferences>,
) : LocalConfigValueProvider {

    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        val preferences = dataStore.data.first()
        val key = createPreferencesKey(param)

        return when (val value = preferences[key]) {
            null -> null
            else -> {
                ConfigValue(
                    value = value,
                    source = ConfigValue.Source.LOCAL
                )
            }
        }
    }

    override suspend fun <T : Any> set(param: ConfigParam<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[createPreferencesKey(param)] = value
        }
    }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        return dataStore.data.map { preferences ->
            val key = createPreferencesKey(param)

            preferences[key]?.let { value ->
                ConfigValue(
                    value = value,
                    source = ConfigValue.Source.LOCAL
                )
            } ?: ConfigValue(
                value = param.defaultValue,
                source = ConfigValue.Source.DEFAULT
            )
        }
    }

    public companion object {
        public const val ID: String = "dev.androidbroadcast.featured.datastore"
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> createPreferencesKey(
    param: ConfigParam<T>,
): Preferences.Key<T> {
    return when (param.valueType) {
        String::class -> stringPreferencesKey(param.key)
        Int::class -> intPreferencesKey(param.key)
        Long::class -> longPreferencesKey(param.key)
        Float::class -> floatPreferencesKey(param.key)
        Double::class -> doublePreferencesKey(param.key)
        Boolean::class -> booleanPreferencesKey(param.key)
        else -> throw IllegalArgumentException("Unsupported type: ${param.valueType}")
    } as Preferences.Key<T>
}
