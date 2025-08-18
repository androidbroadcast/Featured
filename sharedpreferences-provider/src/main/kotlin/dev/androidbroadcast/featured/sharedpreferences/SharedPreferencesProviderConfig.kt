package dev.androidbroadcast.featured.sharedpreferences

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
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

    private val savers: ValueSavers = ValueSavers()
    private val context: CoroutineContext = Dispatchers.IO + context
    private val changedKeysFlow = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)

    init {
        with(savers) {
            put<Int>(IntValueSaver())
            put<Boolean>(BooleanValueSaver())
            put<Float>(FloatValueSaver())
            put<Long>(LongValueSaver())
            put<Double>(DoubleValueSaver())
            put<String>(StringValueSaver())
        }
    }

    override suspend fun <T : Any> get(
        param: ConfigParam<T>,
    ): ConfigValue<T>? {
        return withContext(context) {
            val valueSaver = savers[param.valueType]
                ?: throw IllegalArgumentException("Unsupported type: ${param.valueType}")
            valueSaver.read(sharedPreferences, param.key)?.let { value ->
                ConfigValue(value, ConfigValue.Source.LOCAL)
            }
        }
    }

    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ): Unit = withContext(context) {
        val valueSaver = savers[param.valueType]
            ?: throw IllegalArgumentException("Unsupported type: ${param.valueType}")
        sharedPreferences.edit(commit = true) {
            valueSaver.write(this, param.key, value)
        }
        changedKeysFlow.tryEmit(param.key)
    }

    /**
     * Removes a feature flag from SharedPreferences.
     *
     * @param key The feature flag key to remove
     */
    public suspend fun remove(
        key: String,
    ): Unit = withContext(context) {
        sharedPreferences.edit(commit = true) {
            remove(key)
        }
        changedKeysFlow.tryEmit(key)
    }

    /**
     * Clears all feature flags from SharedPreferences.
     */
    public suspend fun clear(): Unit = withContext(context) {
        sharedPreferences.edit(commit = true) {
            clear()
        }
    }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        return flow<ConfigValue<T>?> {
            emit(get(param)) // Emit the current value first
            emitAll( // Then emit changes
                changedKeysFlow
                    .filter { key -> key == param.key }
                    .map { get(param) }
            )
        }.filterNotNull()
            .distinctUntilChanged() // Avoid emitting the same value multiple times
    }
}
