package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * A [LocalConfigValueProvider] that stores configuration overrides in memory.
 *
 * Values are held in a plain [Map] and are lost when the process terminates. This makes
 * [InMemoryConfigValueProvider] ideal for tests, Compose previews, and ephemeral runtime
 * overrides that do not need to survive process death.
 *
 * [set] and [resetOverride] emit a change signal that causes active [observe] flows to
 * re-evaluate and emit the updated value. [clear] does not emit signals — see its KDoc.
 *
 * ```kotlin
 * val provider = InMemoryConfigValueProvider()
 * val configValues = ConfigValues(localProvider = provider)
 *
 * provider.set(DarkModeParam, true)
 * val value = configValues.getValue(DarkModeParam) // ConfigValue(true, LOCAL)
 * ```
 */
public class InMemoryConfigValueProvider : LocalConfigValueProvider {
    private var storage: Map<String, Any> = emptyMap()
    private val changedKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = 1000)

    /**
     * Returns the locally stored value for [param], or `null` if no override has been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
        storage[param.key]?.let { value ->
            ConfigValue(
                value as T,
                source = ConfigValue.Source.LOCAL,
            )
        }

    /**
     * Stores [value] as a local override for [param] and notifies active [observe] flows.
     *
     * @param param The configuration parameter to override.
     * @param value The value to store.
     */
    public override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        storage += param.key to value
        changedKeyFlow.emit(param.key)
    }

    /**
     * Removes the stored override for [param] and notifies active [observe] flows.
     *
     * After this call, [get] returns `null` for [param] and [ConfigValues] falls back to
     * the remote provider or [ConfigParam.defaultValue].
     *
     * @param param The configuration parameter whose override should be cleared.
     */
    public override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        storage = storage - param.key
        changedKeyFlow.emit(param.key)
    }

    /**
     * Removes all stored overrides, resetting the provider to an empty state.
     *
     * Unlike [resetOverride], this does **not** emit change signals. Callers that need
     * reactive updates after a bulk clear should call [resetOverride] per param instead.
     */
    public fun clear() {
        storage = emptyMap()
    }

    /**
     * Returns a [kotlinx.coroutines.flow.Flow] that emits the current value for [param]
     * immediately (if an override exists) and then emits again on every subsequent [set]
     * or [resetOverride] call for the same key.
     *
     * The flow completes only when the collector's scope is cancelled. It does **not** emit
     * after [clear] because [clear] does not signal individual key changes.
     *
     * @param param The configuration parameter to observe.
     * @return A cold [kotlinx.coroutines.flow.Flow] of [ConfigValue] snapshots.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        flow {
            get(param)?.let { emit(it) }

            changedKeyFlow
                .filter { key -> key == param.key }
                .mapNotNull { get(param) }
                .let { emitAll(it) }
        }
}
