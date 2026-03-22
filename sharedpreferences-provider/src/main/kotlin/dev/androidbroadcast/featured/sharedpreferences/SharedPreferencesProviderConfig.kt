package dev.androidbroadcast.featured.sharedpreferences

import android.content.SharedPreferences
import androidx.core.content.edit
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

/**
 * A [LocalConfigValueProvider] backed by Android [SharedPreferences].
 *
 * Values are persisted in the provided [SharedPreferences] file and survive process restarts.
 * All reads and writes are dispatched on [kotlinx.coroutines.Dispatchers.IO] merged with an
 * optional caller-supplied [CoroutineContext].
 *
 * Supported value types: [String], [Int], [Long], [Float], [Double], [Boolean].
 * Attempting to read or write an unsupported type throws [IllegalArgumentException].
 *
 * Active [observe] flows receive updates whenever [set], [resetOverride], or [remove] is called
 * for the observed key.
 *
 * ```kotlin
 * val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
 * val provider = SharedPreferencesProviderConfig(prefs)
 * val configValues = ConfigValues(localProvider = provider)
 * ```
 *
 * @param sharedPreferences The [SharedPreferences] instance used for persistence.
 * @param context Additional [CoroutineContext] elements merged with [kotlinx.coroutines.Dispatchers.IO]
 *   for all IO-bound operations. Defaults to [EmptyCoroutineContext].
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

    /**
     * Registers a [TypeConverter] for [klass], enabling [set] and [get] for custom types
     * (e.g. enums) that are serialized as strings in [SharedPreferences].
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
        savers[klass] = TypeConverterValueSaver(converter)
    }

    /**
     * Returns the persisted value for [param], or `null` if it has not been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
        withContext(context) {
            val valueSaver =
                savers[param.valueType]
                    ?: throw IllegalArgumentException("Unsupported type: ${param.valueType}")
            valueSaver.read(sharedPreferences, param.key)?.let { value ->
                ConfigValue(value, ConfigValue.Source.LOCAL)
            }
        }

    /**
     * Persists [value] as a local override for [param] and notifies active [observe] flows.
     *
     * The write is performed with `commit = true` (synchronous) on the IO dispatcher.
     *
     * @param param The configuration parameter to override.
     * @param value The value to persist.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ): Unit =
        withContext(context) {
            val valueSaver =
                savers[param.valueType]
                    ?: throw IllegalArgumentException("Unsupported type: ${param.valueType}")
            sharedPreferences.edit(commit = true) {
                valueSaver.write(this, param.key, value)
            }
            changedKeysFlow.tryEmit(param.key)
        }

    /**
     * Removes the persisted override for [param], delegating to [remove].
     *
     * After this call, [get] returns `null` and [ConfigValues] falls back to the remote
     * provider or [ConfigParam.defaultValue].
     *
     * @param param The configuration parameter whose override should be cleared.
     */
    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>): Unit = remove(param.key)

    /**
     * Removes a feature flag from SharedPreferences.
     *
     * @param key The feature flag key to remove
     */
    public suspend fun remove(key: String): Unit =
        withContext(context) {
            sharedPreferences.edit(commit = true) {
                remove(key)
            }
            changedKeysFlow.tryEmit(key)
        }

    /**
     * Removes all feature flags from SharedPreferences.
     *
     * After this call, [get] returns `null` for every parameter that was previously set,
     * and [ConfigValues] falls back to the remote provider or [ConfigParam.defaultValue].
     */
    public override suspend fun clear(): Unit =
        withContext(context) {
            sharedPreferences.edit(commit = true) {
                clear()
            }
        }

    /**
     * Returns a [Flow] that emits a [ConfigValue] for [param] on every change to its key.
     *
     * The flow emits the current persisted value immediately (skipping `null` if unset) and
     * then emits again whenever [set], [resetOverride], or [remove] is called for the same
     * key. Consecutive identical values are deduplicated via `distinctUntilChanged`.
     *
     * @param param The configuration parameter to observe.
     * @return A cold [Flow] that completes when the collector's scope is cancelled.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        return flow<ConfigValue<T>?> {
            emit(get(param)) // Emit the current value first
            emitAll( // Then emit changes
                changedKeysFlow
                    .filter { it == param.key }
                    .map { get(param) },
            )
        }.filterNotNull()
            .distinctUntilChanged() // Avoid emitting the same value multiple times
    }
}

/**
 * Registers a [TypeConverter] for the reified type [T] on this [SharedPreferencesProviderConfig],
 * enabling [SharedPreferencesProviderConfig.set] and [SharedPreferencesProviderConfig.get] for
 * custom types (e.g. enums) serialized as strings in [android.content.SharedPreferences].
 *
 * Inline convenience wrapper that avoids passing [kotlin.reflect.KClass] explicitly:
 * ```kotlin
 * provider.registerConverter(enumConverter<CheckoutVariant>())
 * ```
 *
 * @param T The non-null custom type to register.
 * @param converter The [TypeConverter] that serializes/deserializes [T] as a [String].
 */
public inline fun <reified T : Any> SharedPreferencesProviderConfig.registerConverter(converter: TypeConverter<T>) {
    registerConverter(T::class, converter)
}
