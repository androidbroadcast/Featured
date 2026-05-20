@file:Suppress("unused")
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.androidbroadcast.featured

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
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
 * the error is reported to [onProviderError] (if set) and the next provider in the chain is tried.
 * This means [getValue] and [observe] never propagate provider exceptions to callers.
 *
 * [fetch] is **not** guarded — the caller explicitly triggers a network operation and is
 * responsible for handling any exceptions it throws.
 *
 * ### Sync read path
 *
 * [getValueCached] reads from an in-memory snapshot without any provider I/O. The snapshot is
 * populated lazily by [getValue], [override], and [fetch]. Before any of these have been called
 * for a given parameter, [getValueCached] returns a [ConfigValue] with
 * [ConfigValue.Source.DEFAULT] wrapping [ConfigParam.defaultValue] — matching Firebase
 * Remote Config's "activate then read sync" contract.
 *
 * Note (Phase-1 limitation): values written directly to a [LocalConfigValueProvider] without
 * going through [ConfigValues.override] bypass the snapshot and will not be visible to
 * [getValueCached] until the next [getValue] or [observe] emission for that parameter.
 *
 * ### Lifecycle
 *
 * [ConfigValues] owns an internal [CoroutineScope] used for the background re-resolution
 * dispatched by [resetOverride]. Call [close] when the instance is no longer needed to cancel
 * that scope. In short-lived test code the scope is cleaned up automatically by the test
 * framework, so [close] may be omitted there.
 *
 * ```kotlin
 * val configValues = ConfigValues(
 *     localProvider  = InMemoryConfigValueProvider(),
 *     remoteProvider = FirebaseConfigValueProvider(),
 *     onProviderError = { error -> log.warn("Provider failed", error) },
 * )
 *
 * // Load cached remote values at app start (no network call)
 * configValues.initialize()
 *
 * // Sync read — safe from any thread; returns DEFAULT until cache is warm
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
 * @param localProvider Optional provider for locally persisted overrides.
 * @param remoteProvider Optional provider for remote configuration values.
 * @param onProviderError Optional callback invoked whenever a provider throws during
 *   [getValue] or [observe]. Use this for logging or observability. Defaults to no-op.
 * @throws IllegalArgumentException if both [localProvider] and [remoteProvider] are `null`.
 */
public class ConfigValues(
    private val localProvider: LocalConfigValueProvider? = null,
    private val remoteProvider: RemoteConfigValueProvider? = null,
    private val onProviderError: (Throwable) -> Unit = {},
) : AutoCloseable {
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
     * Internal scope for background re-resolution dispatched by [resetOverride].
     * Uses [SupervisorJob] so that a failure in one re-resolution does not cancel others.
     * Cancelled by [close].
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
     * - [getValue] — performs an async resolution and writes through to the snapshot,
     * - [fetch]    — pulls fresh values from the remote provider (bulk warm-up in Phase 2),
     * - [override] — sets a local override and writes through to the snapshot.
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
                onProviderError(error)
                null
            }
        if (localValue != null) {
            writeSnapshot(param, localValue)
            return localValue
        }

        val remoteValue =
            remoteProvider?.runCatching { get(param) }?.getOrElse { error ->
                onProviderError(error)
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
     * After the local override is cleared, a background coroutine re-resolves the effective
     * value through the full provider priority chain and updates the sync snapshot. Until that
     * coroutine completes, [getValueCached] may still return the previous override value.
     *
     * The background coroutine runs on [Dispatchers.Default] and is owned by an internal
     * [CoroutineScope]; call [close] to cancel it when this [ConfigValues] instance is
     * no longer needed.
     *
     * @param param The configuration parameter whose local override should be cleared.
     */
    public suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        localProvider?.resetOverride(param)
        // Re-resolve via the full priority chain and write through so the snapshot converges
        // to remote/default rather than staying at the stale LOCAL value (Option B from design).
        backgroundScope.launch {
            val resolved = getValue(param)
            writeSnapshot(param, resolved)
        }
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
     * **Phase-2 note:** bulk snapshot warm-up via `SnapshotConfigValueProvider` is not yet wired
     * here. The sync snapshot remains empty after [initialize] until individual params are
     * resolved via [getValue] or [observe].
     */
    public suspend fun initialize() {
        (remoteProvider as? InitializableConfigValueProvider)?.initialize()
    }

    /**
     * Fetches the latest configuration values from the remote provider and activates them.
     * Any active [observe] flows will re-emit the updated value for the observed parameter.
     * Has no effect when no remote provider is configured.
     *
     * **Phase-2 note:** bulk snapshot warm-up after fetch (via `SnapshotConfigValueProvider`)
     * is not yet implemented. The snapshot is updated lazily per-param as [observe] or
     * [getValue] callers process the [fetchSignal].
     */
    public suspend fun fetch() {
        if (remoteProvider == null) return
        remoteProvider.fetch(true)
        fetchSignal.emit(Unit)
    }

    /**
     * Observes changes to the configuration value for the given parameter.
     *
     * Emits the latest value immediately, then continues to emit updates whenever:
     * - the value changes via the local provider, **or**
     * - [fetch] completes and the remote provider returns a new value.
     *
     * Note: local provider emissions that bypass [ConfigValues.override] (i.e. direct calls to
     * the provider's own `set` method) do not write through to the sync snapshot via this flow.
     * They are still emitted reactively but the snapshot is only updated via the [getValue]
     * call in `remoteFlow`. This is a Phase-1 limitation.
     *
     * @param param The configuration parameter to observe.
     * @return A [Flow] of [ConfigValue] for the specified parameter.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        val localFlow = localProvider?.observe(param)?.catch { e -> onProviderError(e) }
        val remoteFlow = fetchSignal.map { getValue(param) }.catch { e -> onProviderError(e) }

        return flow<ConfigValue<T>> {
            emit(getValue(param))
            val merged = if (localFlow != null) merge(localFlow, remoteFlow) else remoteFlow
            merged.collect { emit(it) }
        }.distinctUntilChanged()
    }

    /**
     * Cancels the internal [CoroutineScope] used for background re-resolution in [resetOverride].
     *
     * Safe to omit in short-lived test code — the test framework cleans up coroutines.
     * Should be called explicitly in production code when this [ConfigValues] instance is
     * no longer needed (e.g. in `onDestroy` or a scope-bound lifecycle hook).
     */
    override fun close() {
        backgroundScope.cancel()
    }

    /** Companion object used as a receiver for extension factories (e.g. ConfigValues.fake). */
    public companion object
}
