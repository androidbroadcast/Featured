@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.androidbroadcast.featured

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update

/**
 * A [LocalConfigValueProvider] that stores configuration overrides in memory.
 *
 * Values are held in an [kotlin.concurrent.atomics.AtomicReference] of an immutable [Map] and are lost when the
 * process terminates. This makes [InMemoryConfigValueProvider] ideal for tests, Compose previews,
 * and ephemeral runtime overrides that do not need to survive process death.
 *
 * [set], [resetOverride], and [clear] emit change signals that cause active [observe] flows to
 * re-evaluate and emit the updated (or default) value.
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
    // AtomicReference wrapping an immutable Map prevents lost updates when resetOverride is called
    // concurrently from multiple coroutines (e.g. parallel Reset All from the debug UI).
    private val storageRef = AtomicReference(emptyMap<String, Any>())
    private val changedKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = 1000)

    /**
     * Returns the locally stored value for [param], or `null` if no override has been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
        storageRef.load()[param.key]?.let { value ->
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
        storageRef.update { it + (param.key to value) }
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
        storageRef.update { it - param.key }
        changedKeyFlow.emit(param.key)
    }

    /**
     * Removes all stored overrides and notifies active [observe] flows for every key that
     * was previously set.
     *
     * After this call, [get] returns `null` for every parameter that was previously overridden,
     * and active [observe] flows emit a [ConfigValue] with [ConfigValue.Source.DEFAULT].
     *
     * The keys snapshot is taken atomically before clearing; each removed key is then emitted
     * into the change-signal flow so that observers can react without calling [resetOverride]
     * per parameter.
     */
    public override suspend fun clear() {
        val removedKeys = storageRef.load().keys.toList()
        storageRef.update { emptyMap() }
        for (key in removedKeys) {
            changedKeyFlow.emit(key)
        }
    }

    /**
     * Returns a [kotlinx.coroutines.flow.Flow] that emits the current value for [param]
     * immediately and then emits again on every subsequent [set], [resetOverride], or [clear]
     * call that affects the same key.
     *
     * Conforms to [LocalConfigValueProvider.observe] contract: the initial emission is always
     * present — [ConfigValue.Source.LOCAL] when a value is stored, [ConfigValue.Source.DEFAULT]
     * wrapping [ConfigParam.defaultValue] otherwise. After [clear], affected observers emit
     * [ConfigValue.Source.DEFAULT].
     *
     * The flow completes only when the collector's scope is cancelled.
     *
     * @param param The configuration parameter to observe.
     * @return A cold [kotlinx.coroutines.flow.Flow] of [ConfigValue] snapshots.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        flow {
            emit(get(param) ?: ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT))

            emitAll(
                changedKeyFlow
                    .filter { key -> key == param.key }
                    .map {
                        // Keep the observe stream alive on storage/converter errors; the error is
                        // reported by the consumer's re-resolve via ConfigValues.getValue.
                        try {
                            get(param) ?: ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                            ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
                        }
                    },
            )
        }
}
