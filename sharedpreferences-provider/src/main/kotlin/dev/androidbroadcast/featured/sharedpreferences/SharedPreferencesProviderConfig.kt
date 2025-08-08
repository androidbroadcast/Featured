package dev.androidbroadcast.featured.sharedpreferences

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implementation of FeatureFlagProvider that uses Android SharedPreferences for local storage.
 * This provider stores and retrieves feature flags from the device's local storage.
 */
public class SharedPreferencesProviderConfig(
    private val sharedPreferences: SharedPreferences,
    context: CoroutineContext = EmptyCoroutineContext,
) : LocalConfigValueProvider {

    private val savers: ValueSavers = ValueSavers().apply {
        put(StringValueSaver())
        put(IntValueSaver())
        put(BooleanValueSaver())
        put(FloatValueSaver())
        put(LongValueSaver())
        put(DoubleValueSaver())
    }

    private val context: CoroutineContext = Dispatchers.IO + context

    override suspend fun <T : Any> get(
        param: ConfigParam<T>,
    ): ConfigValue<T>? {
        return withContext(context) {
            val value = savers[param.valueType]?.read(sharedPreferences, param.key)
                ?: throw IllegalArgumentException("Unsupported type: ${param.valueType}")
            ConfigValue(value, ConfigValue.Source.LOCAL)
        }
    }

    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ): Unit = withContext(context) {
        val valueSaver = savers[param.valueType]
            ?: throw IllegalArgumentException("Unsupported type: ${param.valueType}")
        sharedPreferences.edit {
            valueSaver.write(this, param.key, value)
        }
    }

    /**
     * Removes a feature flag from SharedPreferences.
     *
     * @param key The feature flag key to remove
     */
    public suspend fun remove(
        key: String,
    ): Unit = withContext(context) {
        sharedPreferences.edit {
            remove(key)
        }
    }

    /**
     * Clears all feature flags from SharedPreferences.
     */
    public suspend fun clear(): Unit = withContext(context) {
        sharedPreferences.edit {
            clear()
        }
    }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        return sharedPreferences.asFlow()
            .filter { it == param.key }
            .mapNotNull { get(param) }
    }
}

private fun SharedPreferences.asFlow(): Flow<String> {
    return callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            trySendBlocking(requireNotNull(changedKey))
        }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
