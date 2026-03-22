@file:Suppress("unused")

package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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
 * // One-shot read — never throws due to provider failure
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
) {
    init {
        require(localProvider != null || remoteProvider != null) {
            "At least one provider (local or remote) must be provided."
        }
    }

    private val fetchSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Returns the current value for [param], applying provider priority.
     *
     * Priority order: local provider → remote provider → [ConfigParam.defaultValue].
     *
     * Provider exceptions are caught and forwarded to [onProviderError]; this function
     * never throws due to provider failure.
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
        if (localValue != null) return localValue

        val remoteValue =
            remoteProvider?.runCatching { get(param) }?.getOrElse { error ->
                onProviderError(error)
                null
            }
        if (remoteValue != null) return remoteValue

        return ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
    }

    /**
     * Overrides the configuration value for the given parameter with a local value.
     * This method is used to set a user-specific value that will take precedence over
     * any remote value for the specified parameter.
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
    }

    /**
     * Clears the local override for the given parameter, so subsequent reads fall back
     * to remote or default values.
     *
     * @param param The configuration parameter whose local override should be cleared.
     */
    public suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        localProvider?.resetOverride(param)
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
     */
    public suspend fun initialize() {
        (remoteProvider as? InitializableConfigValueProvider)?.initialize()
    }

    /**
     * Fetches the latest configuration values from the remote provider and activates them.
     * Any active [observe] flows will re-emit the updated value for the observed parameter.
     * Has no effect when no remote provider is configured.
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
     * @param param The configuration parameter to observe.
     * @return A [Flow] of [ConfigValue] for the specified parameter.
     */
    public fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        val localFlow = localProvider?.observe(param)
        val remoteFlow = fetchSignal.map { getValue(param) }

        return flow<ConfigValue<T>> {
            emit(getValue(param))
            val merged = if (localFlow != null) merge(localFlow, remoteFlow) else remoteFlow
            merged.collect { emit(it) }
        }.distinctUntilChanged()
    }

    /** Companion object used as a receiver for extension factories (e.g. ConfigValues.fake). */
    public companion object
}
