@file:Suppress("unused")
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.androidbroadcast.featured

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.update

/**
 * Central access point for reading, overriding, and observing configuration values.
 *
 * [ConfigValues] composes an optional [LocalConfigValueProvider] and an optional
 * [RemoteConfigValueProvider]. The final value is selected by a pluggable
 * [ValueResolutionStrategy]; the built-in [ValueResolutionStrategy.Default] applies
 * `local ?: remote ?: default` to the candidates it is given.
 *
 * Which candidates are read depends on [ConfigParam.scope]:
 * 1. **[FlagScope.LOCAL]** — local provider, then this param's default. Remote is not called.
 * 2. **[FlagScope.REMOTE]** — remote provider, then this param's default. Resolution does not
 *    read the local provider, so a stored override of that param is not a candidate.
 * 3. **[FlagScope.ANY]** — local provider, then remote provider, then this param's default.
 *    This is the chain of a [ConfigParam] that omits [ConfigParam.scope].
 *
 * At least one provider must be supplied; passing `null` for both throws [IllegalArgumentException].
 *
 * Provider calls inside [getValue] and [observe] are wrapped in try/catch. If a provider throws,
 * the error is logged to the platform log by default via [onProviderError] and the strategy
 * receives `null` in place of that provider's value. [getValue] and [observe] never propagate
 * provider exceptions to callers.
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
 * @param resolutionStrategy Policy that selects the final value from the candidates this
 *   container supplies. A side skipped because of [ConfigParam.scope] is null and was not read.
 *   Defaults to [ValueResolutionStrategy.Default] (`local ?: remote ?: default` on those
 *   candidates). Exceptions thrown by the strategy itself are not caught.
 * @throws IllegalArgumentException if both [localProvider] and [remoteProvider] are `null`.
 */
public class ConfigValues(
    private val localProvider: LocalConfigValueProvider? = null,
    private val remoteProvider: RemoteConfigValueProvider? = null,
    private val onProviderError: (Throwable) -> Unit = ::logProviderError,
    private val resolutionStrategy: ValueResolutionStrategy = ValueResolutionStrategy.Default,
) {
    init {
        require(localProvider != null || remoteProvider != null) {
            "At least one provider (local or remote) must be provided."
        }
    }

    private val fetchSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * In-memory snapshot of the most recently resolved [ConfigValue] per storage slot.
     *
     * Key: [ConfigParam.localStorageKey] (scope name, a colon, and the public key). Value:
     * [ConfigValue] as resolved at last write time.
     *
     * Two [ConfigParam] instances with the same scope and the same public key map to the same
     * snapshot slot; the last write wins, including across aggregated modules. A local param and
     * a remote param that share a public key do not share a slot.
     *
     * Written via copy-on-write using [AtomicReference.update]; reads via [AtomicReference.load]
     * are always consistent snapshots. Thread-safe on all KMP targets.
     */
    private val snapshot = AtomicReference<Map<String, ConfigValue<*>>>(emptyMap())

    /**
     * Registry of params explicitly registered for automatic snapshot refresh, keyed by
     * [ConfigParam.localStorageKey].
     *
     * Populated only via [warmUp]. [fetch] and [initialize] refresh all params in this map's
     * values so the snapshot remains current after each provider round-trip. The map is
     * intentionally separate from the snapshot: [clearOverrides] KDoc guarantees that
     * [ConfigValues] does not maintain a registry of all known params — the snapshot itself stays
     * slot-only. This warm-set is an explicit opt-in registry built from caller-supplied params.
     *
     * Keyed by [ConfigParam.localStorageKey]: re-registering a param with the same scope and key
     * replaces the previous descriptor (last registration wins). A different scope is a different
     * entry, even when the public key matches.
     */
    private val warmSet = AtomicReference<Map<String, ConfigParam<*>>>(emptyMap())

    /**
     * Forwards [error] to [onProviderError], swallowing any exception the handler itself throws.
     *
     * This ensures that a misbehaving error handler cannot break the read path — [getValue] and
     * [observe] must remain safe for callers regardless of what [onProviderError] does.
     *
     * [CancellationException] thrown by the handler is re-thrown to preserve structured
     * concurrency — a custom handler that internally hits a cancelled scope must not be silently
     * swallowed.
     */
    private fun reportProviderError(error: Throwable) {
        try {
            onProviderError(error)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            // handler must not break the read path
        }
    }

    /** Writes [configValue] into the snapshot under [param]'s storage slot (copy-on-write). */
    private fun <T : Any> writeSnapshot(
        param: ConfigParam<T>,
        configValue: ConfigValue<T>,
    ) {
        snapshot.update { current -> current + (param.localStorageKey() to configValue) }
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
     * **Duplicate-slot semantics:** two [ConfigParam] instances with the same scope and the same
     * public key share one snapshot slot; the last write wins, including across modules. A
     * different scope is a different slot. A cold slot returns this param's own default, not
     * another scope's entry.
     *
     * Thread-safe. Safe to call from any thread, including the Android main thread.
     *
     * @param param The configuration parameter to read.
     * @return The cached [ConfigValue], or a [ConfigValue.Source.DEFAULT] wrapper if the cache
     *   has not been populated for this parameter yet.
     */
    public fun <T : Any> getValueCached(param: ConfigParam<T>): ConfigValue<T> {
        val cached = snapshot.load()[param.localStorageKey()]
        @Suppress("UNCHECKED_CAST") // safe: written by writeSnapshot<T> which enforces T at write time
        return if (cached != null) {
            cached as ConfigValue<T>
        } else {
            // cold-read before cache is warm returns DEFAULT
            defaultConfigValue(param)
        }
    }

    /**
     * Reads [param] from the local provider, mapping provider failure to `null`.
     *
     * runCatching captures CancellationException — it is re-thrown so coroutine cancellation
     * is not silently swallowed as a provider error.
     */
    private suspend fun <T : Any> readLocal(param: ConfigParam<T>): ConfigValue<T>? =
        localProvider?.runCatching { get(param) }?.getOrElse { error ->
            if (error is CancellationException) throw error
            reportProviderError(error)
            null
        }

    /** Reads [param] from the remote provider, mapping provider failure to `null`. */
    private suspend fun <T : Any> readRemote(param: ConfigParam<T>): ConfigValue<T>? =
        remoteProvider?.runCatching { get(param) }?.getOrElse { error ->
            if (error is CancellationException) throw error
            reportProviderError(error)
            null
        }

    /** [ConfigParam.defaultValue] wrapped as the resolution fallback. */
    @Suppress("HardcodedFlagValue") // intentional: this IS the provider fallback path
    private fun <T : Any> defaultConfigValue(param: ConfigParam<T>): ConfigValue<T> =
        ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)

    /**
     * Returns the current value for [param] as selected by the [ValueResolutionStrategy].
     *
     * Candidates are selected by [ConfigParam.scope] before the strategy runs. [FlagScope.ANY]
     * reads local, then remote. [FlagScope.LOCAL] does not call the remote provider, and the
     * strategy receives `remoteValue = null`. [FlagScope.REMOTE] does not call the local
     * provider, and the strategy receives `localValue = null`. With
     * [ValueResolutionStrategy.Default] the result is `local ?: remote ?: default` on those
     * candidates, which for [FlagScope.ANY] is local, then remote, then [ConfigParam.defaultValue].
     *
     * Provider exceptions are caught and forwarded to [onProviderError], and the strategy
     * receives `null` in place of the failed provider's value; this function never throws due
     * to provider failure. A provider that was not called produces no [onProviderError].
     *
     * The resolved value is written through to the sync snapshot under [ConfigParam.localStorageKey]
     * so subsequent calls to [getValueCached] for the same slot reflect this result without
     * further I/O.
     *
     * @param param The configuration parameter to read.
     * @return The resolved [ConfigValue], never `null`.
     */
    public suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T> {
        val resolved = resolveForScope(param)
        // Do not write DEFAULT into the snapshot: a later override / fetch should still win.
        if (resolved.source != ConfigValue.Source.DEFAULT) {
            writeSnapshot(param, resolved)
        }
        return resolved
    }

    /**
     * Supplies the candidates [param]'s scope allows, then asks [resolutionStrategy] to pick.
     *
     * A skipped side is not called and is passed as null. [knownLocal] is the value [override]
     * just wrote. It is a candidate only when the local side is read, so an override of a
     * [FlagScope.REMOTE] param does not become `localValue`.
     */
    private suspend fun <T : Any> resolveForScope(
        param: ConfigParam<T>,
        knownLocal: ConfigValue<T>? = null,
    ): ConfigValue<T> {
        val localValue =
            when (param.scope) {
                FlagScope.REMOTE -> null
                FlagScope.LOCAL, FlagScope.ANY -> knownLocal ?: readLocal(param)
            }
        val remoteValue =
            when (param.scope) {
                FlagScope.LOCAL -> null
                FlagScope.REMOTE, FlagScope.ANY -> readRemote(param)
            }
        return resolutionStrategy.resolve(
            param = param,
            localValue = localValue,
            remoteValue = remoteValue,
            defaultValue = defaultConfigValue(param),
        )
    }

    /**
     * Writes [value] into [param]'s local slot and refreshes that slot's snapshot.
     *
     * The write uses [ConfigParam.localStorageKey] and does not touch another scope's slot or
     * the bare public key. For [FlagScope.LOCAL] and [FlagScope.ANY], the written value is the
     * local candidate. For [FlagScope.REMOTE], the strategy still receives `localValue = null`:
     * under [ValueResolutionStrategy.Default], [getValue] of that remote flag stays
     * `remote ?: default`. A custom strategy may combine or ignore the candidates it is given.
     *
     * Usually used for testing purposes or to allow users to customize.
     *
     * @param param The configuration parameter to override.
     */
    public suspend fun <T : Any> override(
        param: ConfigParam<T>,
        value: T,
    ) {
        if (localProvider == null) return
        localProvider.set(param, value)
        val resolved =
            resolveForScope(
                param = param,
                knownLocal = ConfigValue(value, ConfigValue.Source.LOCAL),
            )
        // Unconditional write (unlike getValue): the override must converge the snapshot even
        // when the strategy resolves to the default — same rule as resetOverride.
        writeSnapshot(param, resolved)
    }

    /**
     * Clears the local slot for [param] only. Another scope's slot, and the bare public key,
     * are not removed.
     *
     * After the slot is cleared, the effective value is re-resolved under [param]'s scope and
     * written through to the sync snapshot. [FlagScope.REMOTE] still passes `localValue = null`
     * into the strategy, including this call.
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
     * The supplied params are also registered in an internal warm-set keyed by
     * [ConfigParam.localStorageKey]. [fetch] and [initialize] automatically refresh all registered
     * params so the snapshot stays current after each provider round-trip. Registration is
     * per-slot idempotent — re-registering a param with the same scope and key replaces the
     * previous descriptor (last registration wins). A different scope is registered separately.
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
        warmSet.update { current -> current + params.associateBy { it.localStorageKey() } }
        refreshWarmSet(params)
    }

    /**
     * Resolves [params] through the provider priority chain in parallel and writes all results
     * into the snapshot in a single atomic batch update.
     *
     * Empty [params] is a no-op — the [coroutineScope] is skipped entirely to avoid overhead.
     *
     * The raw resolved list (including DEFAULT-sourced entries) is passed directly to
     * [mergeWarmResults], which is responsible for filtering out DEFAULT-sourced entries before
     * writing them into the snapshot.
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
                            param.localStorageKey() to getValue(typedParam)
                        }
                    }.awaitAll()
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
     * If the provider's [InitializableConfigValueProvider.initialize] throws, the exception
     * propagates to the caller and the warm-set refresh is skipped. [getValueCached] keeps
     * returning defaults for warmed params until a later [initialize] or [warmUp] call succeeds.
     *
     * **Latency note:** this function returns after the warm-set refresh completes. Latency
     * grows proportionally to the number of registered params times the provider's per-param
     * resolution cost. Params never passed to [warmUp] and never individually read are not
     * refreshed here.
     */
    public suspend fun initialize() {
        (remoteProvider as? InitializableConfigValueProvider)?.initialize()
        refreshWarmSet(warmSet.load().values)
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
        refreshWarmSet(warmSet.load().values)
    }

    /**
     * Observes changes to the configuration value for the given parameter.
     *
     * Every emission is a **fully resolved value** obtained by calling [getValue], which applies
     * the configured [ValueResolutionStrategy] (by default: local provider → remote provider →
     * [ConfigParam.defaultValue]).
     * Local-provider emissions are used **only as change signals** — their payloads are discarded
     * and never re-emitted directly. This prevents a stale [ConfigValue.Source.DEFAULT] from
     * the local provider (for an absent key) from clobbering a remote value already known to
     * [getValue].
     *
     * The flow emits once immediately upon collection, then on every:
     * - local-provider change signal (i.e. after [override] or direct provider [set] /
     *   [resetOverride]), **or**
     * - [fetch] completion.
     *
     * Consecutive identical resolved values are deduplicated via `distinctUntilChanged`.
     * Deduplication compares full [ConfigValue] equality — both value AND source must match for
     * a frame to be suppressed.
     *
     * **Known cost:** each local-provider signal triggers one full [getValue] call under this
     * param's scope. A write to another scope's slot is not that signal. [fetch] still signals
     * observers; a [FlagScope.LOCAL] resolve does not read remote, and `distinctUntilChanged`
     * drops an unchanged result.
     *
     * Note: local-provider direct writes (bypassing [ConfigValues.override]) reach observers
     * reactively but do **not** write through to the snapshot. Use [ConfigValues.override] if
     * [getValueCached] must reflect the write.
     *
     * @param param The configuration parameter to observe.
     * @return A [Flow] of fully resolved [ConfigValue] that never terminates on provider error.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        // Local emissions are used only as signals — map payload to Unit to make intent explicit.
        val localTrigger: Flow<Unit> =
            localProvider
                ?.observe(param)
                ?.map { }
                // A third-party provider whose observe() throws must not permanently silence local
                // change signals; bounded backoff avoids a hot loop. CancellationException is not
                // caught by retryWhen — structured concurrency remains intact.
                ?.retryWhen { cause, attempt ->
                    reportProviderError(cause)
                    delay(minOf(attempt + 1, 10) * 100L)
                    true
                }
                ?: emptyFlow()
        val remoteTrigger: Flow<Unit> = fetchSignal

        return flow {
            emit(getValue(param))
            merge(localTrigger, remoteTrigger).collect {
                emit(getValue(param))
            }
        }.distinctUntilChanged()
    }

    /** Companion object used as a receiver for extension factories (e.g. ConfigValues.fake). */
    public companion object
}

/**
 * Merges a batch of freshly resolved warm-set entries into the current snapshot, applying two
 * rules:
 *
 * 1. **DEFAULT-drop rule:** entries whose resolved value has [ConfigValue.Source.DEFAULT] are
 *    silently dropped and never written into the snapshot. This is consistent with
 *    [ConfigValues.getValue], which also never writes DEFAULT into the snapshot, so a later
 *    override or fetch can still win.
 *
 * 2. **LOCAL-protection rule:** if the current snapshot already holds a LOCAL-sourced value for
 *    a key and the incoming resolved value is **not** LOCAL-sourced, the current value wins.
 *    This protects against the race where a [ConfigValues.override] call (writing LOCAL into the
 *    snapshot) lands after the warm-refresh batch read its providers but before the batch commits.
 *    Without this rule the batch commit would silently clobber that override. The provider
 *    remains the source of truth and the next refresh or [ConfigValues.getValue] call self-heals.
 *
 * For every other combination (current is non-LOCAL, or resolved is LOCAL, or the slot is
 * absent) the resolved value wins, so fresh provider data always propagates correctly.
 *
 * Callers pass the raw resolved list — no pre-filtering is required.
 *
 * @param current The snapshot map at the moment the atomic update fires.
 * @param resolved Raw resolved entries from the latest provider pass (may include DEFAULT-sourced).
 * @return A new map reflecting the merged state.
 */
internal fun mergeWarmResults(
    current: Map<String, ConfigValue<*>>,
    resolved: List<Pair<String, ConfigValue<*>>>,
): Map<String, ConfigValue<*>> {
    if (resolved.isEmpty()) return current
    val result = current.toMutableMap()
    resolved.forEach { (key, resolvedValue) ->
        // DEFAULT-drop rule: never write DEFAULT into the snapshot.
        if (resolvedValue.source == ConfigValue.Source.DEFAULT) return@forEach
        val currentValue = current[key]
        // LOCAL-protection rule: keep the current snapshot value when an override landed
        // mid-flight — current slot is LOCAL and the incoming resolved value is non-LOCAL.
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
