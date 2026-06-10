@file:Suppress("unused")
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.androidbroadcast.featured

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update

/**
 * Central access point for reading, overriding, and observing configuration values.
 *
 * [ConfigValues] composes an optional [LocalConfigValueProvider] and an optional
 * [RemoteConfigValueProvider] using a well-defined priority order:
 * 1. **Local provider** — highest priority; used for user-specific overrides.
 * 2. **Remote provider** — values fetched from a remote configuration service.
 * 3. **Default** — [ConfigParam.defaultValue] is used when no provider returns a value.
 *
 * At least one provider must be supplied; passing `null` for both throws [IllegalArgumentException].
 *
 * Provider calls inside [getValue] and [observe] are wrapped in try/catch. If a provider throws,
 * the error is logged to the platform log by default via [onProviderError] and the next provider
 * in the chain is tried. [getValue] and [observe] never propagate provider exceptions to callers.
 *
 * [fetch] is **not** guarded — the caller explicitly triggers a network operation and is
 * responsible for handling any exceptions it throws.
 *
 * ### Sync read path
 *
 * [getValueCached] reads from an in-memory snapshot without any provider I/O. The snapshot is
 * populated lazily by [getValue], [override], [fetch], and [warmUp]. Before any of these have
 * been called for a given parameter, [getValueCached] returns a [ConfigValue] with
 * [ConfigValue.Source.DEFAULT] wrapping [ConfigParam.defaultValue] — matching Firebase
 * Remote Config's "activate then read sync" contract.
 *
 * To ensure the snapshot is pre-populated for all known params before the first synchronous read,
 * call [warmUp] with the full parameter set (typically `GeneratedFeaturedRegistry.all`) after
 * [initialize]. After [warmUp] completes, [getValueCached] returns provider-resolved values
 * for every warmed param. [fetch] and [initialize] automatically refresh the warm-set so the
 * snapshot stays current after each network round-trip.
 *
 * Note: values written directly to a [LocalConfigValueProvider] without going through
 * [ConfigValues.override] bypass the snapshot and will not be visible to [getValueCached]
 * until the next [getValue] or [observe] emission for that parameter.
 *
 * ```kotlin
 * val configValues = ConfigValues(
 *     localProvider  = InMemoryConfigValueProvider(),
 *     remoteProvider = FirebaseConfigValueProvider(),
 *     onProviderError = { error -> log.warn("Provider failed", error) },
 * )
 *
 * // Load cached remote values at app start (no network call), then pre-warm the snapshot
 * configValues.initialize()
 * configValues.warmUp(GeneratedFeaturedRegistry.all)
 *
 * // Sync read — safe from any thread; returns provider-resolved values after warmUp
 * val enabled: Boolean = configValues.getValueCached(DarkModeParam).value
 *
 * // One-shot async read — never throws due to provider failure; also warms the cache
 * val value: ConfigValue<Boolean> = configValues.getValue(DarkModeParam)
 *
 * // Reactive observation — flow does not terminate on provider error
 * configValues.observe(DarkModeParam).collect { configValue ->
 *     applyTheme(configValue.value)
 * }
 * ```
 *
 * ### Multi-module wiring
 *
 * When wiring a multi-module application, construct one [ConfigValues] per feature module so each
 * module sees only the flags it declares. All [ConfigValues] instances should share the same
 * [LocalConfigValueProvider] (and [RemoteConfigValueProvider], if any) — the provider is the
 * single source of truth for stored overrides, and its reactive [observe] flow propagates writes
 * from any [ConfigValues] instance to every other one that shares the provider. A debug screen
 * that exposes every flag across modules is just one extra [ConfigValues] built from the same
 * shared providers and driven by `GeneratedFeaturedRegistry.all`.
 *
 * @param localProvider Optional provider for locally persisted overrides.
 * @param remoteProvider Optional provider for remote configuration values.
 * @param onProviderError Callback invoked whenever a provider throws during [getValue] or
 *   [observe]. Defaults to logging the error to the platform log (Android: `Log.w`,
 *   iOS: `NSLog`, JVM: `System.err`). Pass `{}` to silence errors; pass a custom handler
 *   to route them to your own observability system. The callback should not throw; any
 *   exception thrown by it is silently ignored to preserve the "errors never propagate
 *   to callers" guarantee.
 * @throws IllegalArgumentException if both [localProvider] and [remoteProvider] are `null`.
 */
public class ConfigValues(
    private val localProvider: LocalConfigValueProvider? = null,
    private val remoteProvider: RemoteConfigValueProvider? = null,
    private val onProviderError: (Throwable) -> Unit = ::logProviderError,
) {
    init {
        require(localProvider != null || remoteProvider != null) {
            "At least one provider (local or remote) must be provided."
        }
    }

    private val fetchSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * In-memory snapshot of the most recently resolved [ConfigValue] per parameter key.
     *
     * Key: [ConfigParam.key]. Value: [ConfigValue] as resolved at last write time.
     *
     * Two [ConfigParam] instances sharing the same [ConfigParam.key] map to the same snapshot
     * slot; the last write wins. Within a single code-generated module keys are unique;
     * cross-module key collisions are theoretically possible and documented as last-write-wins.
     *
     * Written via copy-on-write using [AtomicReference.update]; reads via [AtomicReference.load]
     * are always consistent snapshots. Thread-safe on all KMP targets.
     */
    private val snapshot = AtomicReference<Map<String, ConfigValue<*>>>(emptyMap())

    /**
     * The set of params explicitly registered for automatic snapshot refresh.
     *
     * Populated only via [warmUp]. [fetch] and [initialize] refresh all params in this set
     * so the snapshot remains current after each provider round-trip. The set is intentionally
     * separate from the snapshot map: [clearOverrides] KDoc guarantees that [ConfigValues] does
     * not maintain a registry of all known params — the snapshot itself stays key-only. The
     * warm-set is an explicit opt-in registry built from caller-supplied params.
     */
    private val warmSet = AtomicReference<Set<ConfigParam<*>>>(emptySet())

    /**
     * Forwards [error] to [onProviderError], swallowing any exception the handler itself throws.
     *
     * This ensures that a misbehaving error handler cannot break the read path — [getValue] and
     * [observe] must remain safe for callers regardless of what [onProviderError] does.
     */
    private fun reportProviderError(error: Throwable) {
        try {
            onProviderError(error)
        } catch (_: Throwable) {
            // handler must not break the read path
        }
    }

    /** Writes [configValue] into the snapshot under [param]'s key (copy-on-write). */
    private fun <T : Any> writeSnapshot(
        param: ConfigParam<T>,
        configValue: ConfigValue<T>,
    ) {
        snapshot.update { current -> current + (param.key to configValue) }
    }

    /**
     * Returns the currently cached [ConfigValue] for [param] without performing any I/O.
     *
     * Returns a [ConfigValue] with [ConfigValue.Source.DEFAULT] wrapping [ConfigParam.defaultValue]
     * until the snapshot is populated by one of:
     * - [getValue]  — performs an async resolution and writes through to the snapshot,
     * - [warmUp]    — pre-populates the snapshot for a supplied collection of params,
     * - [fetch]     — pulls fresh values and refreshes every param registered via [warmUp],
     * - [initialize] — loads cached remote values and refreshes the warm-set,
     * - [override]  — sets a local override and writes through to the snapshot.
     *
     * **Duplicate-key semantics:** two [ConfigParam] instances with the same [ConfigParam.key]
     * share one snapshot slot; the last write wins. Codegen guarantees uniqueness within a
     * module; cross-module collisions are possible and intentionally handled this way.
     *
     * Thread-safe. Safe to call from any thread, including the Android main thread.
     *
     * @param param The configuration parameter to read.
     * @return The cached [ConfigValue], or a [ConfigValue.Source.DEFAULT] wrapper if the cache
     *   has not been populated for this parameter yet.
     */
    public fun <T : Any> getValueCached(param: ConfigParam<T>): ConfigValue<T> {
        val cached = snapshot.load()[param.key]
        @Suppress("UNCHECKED_CAST") // safe: written by writeSnapshot<T> which enforces T at write time
        return if (cached != null) {
            cached as ConfigValue<T>
        } else {
            @Suppress("HardcodedFlagValue") // intentional: cold-read before cache is warm returns DEFAULT
            ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
        }
    }

    /**
     * Returns the current value for [param], applying provider priority.
     *
     * Priority order: local provider → remote provider → [ConfigParam.defaultValue].
     *
     * Provider exceptions are caught and forwarded to [onProviderError]; this function
     * never throws due to provider failure.
     *
     * The resolved value is written through to the sync snapshot so subsequent calls to
     * [getValueCached] for the same parameter reflect this result without further I/O.
     *
     * @param param The configuration parameter to read.
     * @return The resolved [ConfigValue], never `null`.
     */
    public suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T> {
        val localValue =
            localProvider?.runCatching { get(param) }?.getOrElse { error ->
                // runCatching captures CancellationException — propagate it so coroutine
                // cancellation is not silently swallowed as a provider error.
                if (error is CancellationException) throw error
                reportProviderError(error)
                null
            }
        if (localValue != null) {
            writeSnapshot(param, localValue)
            return localValue
        }

        val remoteValue =
            remoteProvider?.runCatching { get(param) }?.getOrElse { error ->
                if (error is CancellationException) throw error
                reportProviderError(error)
                null
            }
        if (remoteValue != null) {
            writeSnapshot(param, remoteValue)
            return remoteValue
        }

        @Suppress("HardcodedFlagValue") // intentional: this IS the provider fallback path
        val defaultValue = ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
        // Do not write DEFAULT into the snapshot: a later override / fetch should still win.
        return defaultValue
    }

    /**
     * Overrides the configuration value for the given parameter with a local value.
     * This method is used to set a user-specific value that will take precedence over
     * any remote value for the specified parameter.
     *
     * After the provider write succeeds, the new value is written through to the sync
     * snapshot so [getValueCached] reflects the override immediately.
     *
     * Usually used for testing purposes or to allow users to customize.
     *
     * @param param The configuration parameter to override.
     */
    public suspend fun <T : Any> override(
        param: ConfigParam<T>,
        value: T,
    ) {
        localProvider?.set(param, value)
        if (localProvider != null) {
            writeSnapshot(param, ConfigValue(value, ConfigValue.Source.LOCAL))
        }
    }

    /**
     * Clears the local override for the given parameter, so subsequent reads fall back
     * to remote or default values.
     *
     * After the local override is cleared, the effective value is re-resolved synchronously
     * through the full provider priority chain and written through to the sync snapshot.
     * [getValueCached] reflects the new value as soon as this function returns.
     *
     * @param param The configuration parameter whose local override should be cleared.
     */
    public suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        localProvider?.resetOverride(param)
        // Re-resolve via the full priority chain and write through so the snapshot converges
        // to remote/default rather than staying at the stale LOCAL value.
        // Explicit writeSnapshot is required because getValue intentionally does not write
        // DEFAULT into the snapshot (see getValue implementation). Without this write, a
        // previously overridden slot would remain stale even when both providers return null.
        val resolved = getValue(param)
        writeSnapshot(param, resolved)
    }

    /**
     * Removes all locally overridden values, resetting the local provider to an empty state.
     *
     * After this call, every [getValue] call falls back to the remote provider or
     * [ConfigParam.defaultValue]. Has no effect when no local provider is configured.
     *
     * Note: the sync snapshot is **not** cleared here. Individual param slots are updated
     * lazily when [getValue] or [resetOverride] is called for each param. This is consistent
     * with the fact that [ConfigValues] does not maintain a registry of all known params.
     */
    public suspend fun clearOverrides() {
        localProvider?.clear()
    }

    /**
     * Pre-populates the sync snapshot for every param in [params] by resolving each one through
     * the full provider priority chain in parallel, then writing all results in a single atomic
     * batch update.
     *
     * After this call, [getValueCached] returns provider-resolved values for every param in
     * [params] without requiring a prior [getValue] or [observe] call. This satisfies the
     * "activate then read sync" pattern used by Firebase Remote Config and similar providers.
     *
     * The supplied params are also registered in an internal warm-set. [fetch] and [initialize]
     * automatically refresh all registered params so the snapshot stays current after each
     * provider round-trip. This registration is idempotent — calling [warmUp] multiple times
     * with the same params does not cause duplication and re-resolves the latest values.
     *
     * Provider errors during warm-up are forwarded to [onProviderError] and do not throw; the
     * affected param resolves to its [ConfigParam.defaultValue] for that round (same semantics
     * as [getValue]). [CancellationException] propagates normally.
     *
     * **Residual limitation:** params never passed to [warmUp] and never individually resolved
     * via [getValue] or [observe] will not appear in the snapshot after [fetch]. Only params
     * explicitly registered here (or individually read) are kept current.
     *
     * @param params The configuration parameters to pre-populate. Typically
     *   `GeneratedFeaturedRegistry.all` for a full application warm-up.
     */
    public suspend fun warmUp(params: Collection<ConfigParam<*>>) {
        warmSet.update { it + params }
        refreshWarmSet(params)
    }

    /**
     * Resolves [params] through the provider priority chain in parallel and writes all results
     * into the snapshot in a single atomic batch update.
     *
     * Empty [params] is a no-op — the [coroutineScope] is skipped entirely to avoid overhead.
     *
     * DEFAULT-sourced results are filtered out before the merge to preserve the invariant that
     * DEFAULT values are never written into the snapshot (consistent with [getValue]).
     */
    private suspend fun refreshWarmSet(params: Collection<ConfigParam<*>>) {
        if (params.isEmpty()) return
        coroutineScope {
            val resolved =
                params
                    .map { param ->
                        async {
                            @Suppress("UNCHECKED_CAST")
                            val typedParam = param as ConfigParam<Any>
                            param.key to getValue(typedParam)
                        }
                    }.awaitAll()
                    .filter { (_, configValue) -> configValue.source != ConfigValue.Source.DEFAULT }
            snapshot.update { current -> mergeWarmResults(current, resolved) }
        }
    }

    /**
     * Loads previously cached remote values into memory without performing a network fetch.
     *
     * Call this once at an appropriate moment during app startup — before any [getValue] calls
     * that require meaningful values — to populate in-memory state from a local cache.
     * After [initialize] completes, [getValue] returns cached values immediately.
     *
     * Has no effect when the remote provider does not implement [InitializableConfigValueProvider],
     * or when no remote provider is configured.
     *
     * Does **not** perform a network fetch; use [fetch] for that.
     *
     * After the provider's [InitializableConfigValueProvider.initialize] call completes, all
     * params previously registered via [warmUp] are refreshed in parallel and written to the
     * snapshot in a single atomic batch update. This ensures [getValueCached] reflects the newly
     * loaded cache values immediately for every warmed param. If no params have been registered
     * via [warmUp], this refresh phase is a no-op with zero overhead.
     *
     * **Latency note:** this function returns after the warm-set refresh completes. Latency
     * grows proportionally to the number of registered params times the provider's per-param
     * resolution cost. Params never passed to [warmUp] and never individually read are not
     * refreshed here.
     */
    public suspend fun initialize() {
        (remoteProvider as? InitializableConfigValueProvider)?.initialize()
        refreshWarmSet(warmSet.load())
    }

    /**
     * Fetches the latest configuration values from the remote provider and activates them.
     * Any active [observe] flows will re-emit the updated value for the observed parameter.
     * Has no effect when no remote provider is configured.
     *
     * After the fetch and [fetchSignal] emission, all params previously registered via [warmUp]
     * are refreshed in parallel and written to the snapshot in a single atomic batch update.
     * Observers receive the [fetchSignal] before the warm-set refresh completes — their
     * individual [getValue] calls race with this refresh and either result is correct.
     *
     * **Latency note:** this function returns after the warm-set refresh completes. Latency
     * grows proportionally to the number of registered params times the provider's per-param
     * resolution cost. Params never passed to [warmUp] and never individually read are not
     * refreshed here.
     */
    public suspend fun fetch() {
        if (remoteProvider == null) return
        remoteProvider.fetch(true)
        fetchSignal.emit(Unit)
        refreshWarmSet(warmSet.load())
    }

    /**
     * Observes changes to the configuration value for the given parameter.
     *
     * Emits the latest value immediately, then continues to emit updates whenever:
     * - the value changes via the local provider, **or**
     * - [fetch] completes and the remote provider returns a new value.
     *
     * Note: local-provider direct emissions (i.e. direct calls to the provider's own `set`
     * method, bypassing [ConfigValues.override]) reach observers reactively but do **not** write
     * through to the snapshot. Use [ConfigValues.override] instead of the provider's `set` if
     * [getValueCached] must reflect the write.
     *
     * @param param The configuration parameter to observe.
     * @return A [Flow] of [ConfigValue] for the specified parameter.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        val localFlow = localProvider?.observe(param)?.catch { e -> reportProviderError(e) }
        val remoteFlow = fetchSignal.map { getValue(param) }.catch { e -> reportProviderError(e) }

        return flow<ConfigValue<T>> {
            emit(getValue(param))
            val merged = if (localFlow != null) merge(localFlow, remoteFlow) else remoteFlow
            merged.collect { emit(it) }
        }.distinctUntilChanged()
    }

    /** Companion object used as a receiver for extension factories (e.g. ConfigValues.fake). */
    public companion object
}

/**
 * Merges a batch of freshly resolved warm-set entries into the current snapshot, applying
 * an override-protection rule: if the current snapshot already holds a LOCAL-sourced value for a
 * key and the incoming resolved value is **not** LOCAL-sourced, the current value wins.
 *
 * This protects against the following race: a [ConfigValues.override] call (which writes
 * LOCAL into the snapshot) may land after the warm-refresh batch read its providers but before
 * the batch commits. Without this rule the batch commit would silently clobber that override.
 *
 * For every other combination (current is non-LOCAL, or resolved is LOCAL, or the slot is
 * absent) the resolved value wins, so fresh provider data always propagates correctly.
 *
 * DEFAULT-sourced entries must be filtered out **before** calling this function; DEFAULT is
 * never written into the snapshot (see [ConfigValues.getValue] KDoc).
 *
 * @param current The snapshot map at the moment the atomic update fires.
 * @param resolved Non-DEFAULT resolved entries from the latest provider pass.
 * @return A new map reflecting the merged state.
 */
internal fun mergeWarmResults(
    current: Map<String, ConfigValue<*>>,
    resolved: List<Pair<String, ConfigValue<*>>>,
): Map<String, ConfigValue<*>> {
    if (resolved.isEmpty()) return current
    val result = current.toMutableMap()
    resolved.forEach { (key, resolvedValue) ->
        val currentValue = current[key]
        // Keep the current snapshot value when an override landed mid-flight:
        // current slot is LOCAL (written by override/writeSnapshot) and the incoming
        // resolved value is non-LOCAL (remote or default). The provider is still the
        // source of truth and the next refresh or getValue call will self-heal.
        val keepCurrent =
            currentValue != null &&
                currentValue.source == ConfigValue.Source.LOCAL &&
                resolvedValue.source != ConfigValue.Source.LOCAL
        if (!keepCurrent) {
            result[key] = resolvedValue
        }
    }
    return result
}
