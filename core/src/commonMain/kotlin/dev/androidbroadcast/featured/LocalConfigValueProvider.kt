package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow

public interface LocalConfigValueProvider : ConfigValueProvider {
    /**
     * Sets the configuration value for the given parameter.
     * This method should be used to update local values,
     * which may not be reflected in remote sources.
     *
     * @param param The configuration parameter to set the value for.
     * @param value The value to set for the specified parameter.
     */
    public suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    )

    /**
     * Removes the locally overridden value for the given parameter, so the next
     * [get] call returns `null` and the effective value falls back to remote or default.
     *
     * @param param The configuration parameter whose local override should be cleared.
     */
    public suspend fun <T : Any> resetOverride(param: ConfigParam<T>)

    /**
     * Removes all locally overridden values, resetting the provider to an empty state.
     *
     * After this call, [get] returns `null` for every parameter that was previously
     * overridden, and [ConfigValues] falls back to the remote provider or
     * [ConfigParam.defaultValue].
     */
    public suspend fun clear()

    /**
     * Observes changes to the configuration value for the given parameter.
     *
     * **Emission contract:** the flow ALWAYS emits exactly one value immediately upon collection:
     * - the locally stored [ConfigValue] with [ConfigValue.Source.LOCAL] if a value has been set, or
     * - [ConfigValue] wrapping [ConfigParam.defaultValue] with [ConfigValue.Source.DEFAULT] if
     *   no value is stored for [param].
     *
     * After the initial emission the flow continues to emit whenever the stored value changes
     * (i.e. after [set] or [resetOverride]). Consecutive identical emissions MAY be deduplicated
     * by the implementation via `distinctUntilChanged`, but the initial emission is always
     * guaranteed regardless of the current value.
     *
     * **Lifetime contract:** the flow MUST never complete normally. It terminates only when the
     * collector's coroutine scope is cancelled.
     *
     * **Error contract:** storage or converter errors MUST NOT terminate the flow. Emit a
     * [ConfigValue] wrapping [ConfigParam.defaultValue] with [ConfigValue.Source.DEFAULT] as a
     * fallback instead. The error is surfaced separately through [ConfigValues.observe] when it
     * re-resolves via [ConfigValues.getValue] and calls [onProviderError][ConfigValues.onProviderError].
     *
     * **After [clear]:** behavior is implementation-defined. [InMemoryConfigValueProvider] does
     * not emit after [clear] because [clear] does not signal individual key changes. Persistent
     * providers backed by system storage (SharedPreferences, NSUserDefaults, DataStore) may or
     * may not emit depending on whether their storage layer fires a change notification.
     * Callers that need reliable reactive updates after a bulk clear should call [resetOverride]
     * per parameter instead.
     *
     * **Payload:** the flow emits [ConfigValue]`<T>` rather than [Unit] so that consumers other
     * than [ConfigValues] — such as custom observers that bypass the priority chain — can use the
     * emitted value directly without an additional [get] call.
     *
     * This contract enables [ConfigValues.observe] to use local emissions purely as change
     * signals and always perform a full priority-chain re-resolve via [ConfigValues.getValue],
     * which prevents stale DEFAULT values from clobbering a remote value that is already known.
     *
     * [DataStoreConfigValueProvider][dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider]
     * is the reference implementation that already conforms to this contract.
     *
     * @param param The configuration parameter to observe.
     * @return A [kotlinx.coroutines.flow.Flow] of [ConfigValue] snapshots that always emits
     *   immediately on collection and then on every subsequent change.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>>
}
