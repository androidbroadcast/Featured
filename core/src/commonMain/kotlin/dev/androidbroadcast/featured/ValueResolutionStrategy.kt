package dev.androidbroadcast.featured

/**
 * Pluggable policy that decides which value [ConfigValues] returns for a parameter once all
 * candidate values have been read.
 *
 * [ConfigValues] reads the local and remote providers first and only then asks the strategy to
 * pick the final value — the strategy is a pure selection function and performs no I/O of its
 * own. The built-in [Default] implementation reproduces the classic priority chain
 * `local ?: remote ?: default`.
 *
 * ### Contract
 *
 * - [localValue] / [remoteValue] are `null` when the corresponding provider is not configured,
 *   has no value for the parameter, or threw. Provider errors are already caught and reported
 *   to `ConfigValues.onProviderError` before the strategy is invoked.
 * - [defaultValue] is never `null`: it wraps [ConfigParam.defaultValue] with
 *   [ConfigValue.Source.DEFAULT] and is always a safe result.
 * - A result with [ConfigValue.Source.DEFAULT] is **not** written to the [ConfigValues] sync
 *   snapshot (the same rule the library applies to its own default fallback); any other result
 *   is written through and becomes visible to `ConfigValues.getValueCached`.
 * - Exceptions thrown by [resolve] are **not** caught by [ConfigValues] — the strategy is
 *   consumer code (same trust model as the `onProviderError` handler) and a throwing strategy
 *   is a programming error that should surface, not be masked.
 *
 * ### Examples
 *
 * Conservative gating — a flag is enabled only when **both** the remote flag and the built-in
 * default are `true` (kill-switch AND):
 *
 * ```kotlin
 * val killSwitchAnd = object : ValueResolutionStrategy {
 *     override fun <T : Any> resolve(
 *         param: ConfigParam<T>,
 *         localValue: ConfigValue<T>?,
 *         remoteValue: ConfigValue<T>?,
 *         defaultValue: ConfigValue<T>,
 *     ): ConfigValue<T> {
 *         val local = (localValue ?: defaultValue).value
 *         val remote = remoteValue?.value
 *         if (local is Boolean && remote is Boolean) {
 *             @Suppress("UNCHECKED_CAST")
 *             return ConfigValue((local && remote) as T, ConfigValue.Source.UNKNOWN)
 *         }
 *         return localValue ?: remoteValue ?: defaultValue
 *     }
 * }
 * ```
 *
 * Remote-only — ignore local overrides entirely, even in debug builds:
 *
 * ```kotlin
 * val remoteOnly = object : ValueResolutionStrategy {
 *     override fun <T : Any> resolve(
 *         param: ConfigParam<T>,
 *         localValue: ConfigValue<T>?,
 *         remoteValue: ConfigValue<T>?,
 *         defaultValue: ConfigValue<T>,
 *     ): ConfigValue<T> = remoteValue ?: defaultValue
 * }
 * ```
 *
 * Pass the strategy when constructing [ConfigValues]:
 *
 * ```kotlin
 * val configValues = ConfigValues(
 *     localProvider = InMemoryConfigValueProvider(),
 *     remoteProvider = FirebaseConfigValueProvider(),
 *     resolutionStrategy = killSwitchAnd,
 * )
 * ```
 */
public interface ValueResolutionStrategy {
    /**
     * Picks the final [ConfigValue] for [param] from the already-resolved candidates.
     *
     * @param param The configuration parameter being resolved.
     * @param localValue Value read from the local provider, or `null` when the provider is
     *   absent, has no value, or failed.
     * @param remoteValue Value read from the remote provider, or `null` when the provider is
     *   absent, has no value, or failed.
     * @param defaultValue [ConfigParam.defaultValue] wrapped with [ConfigValue.Source.DEFAULT];
     *   always non-null and safe to return.
     * @return The value [ConfigValues] hands to the caller; never `null`.
     */
    public fun <T : Any> resolve(
        param: ConfigParam<T>,
        localValue: ConfigValue<T>?,
        remoteValue: ConfigValue<T>?,
        defaultValue: ConfigValue<T>,
    ): ConfigValue<T>

    /**
     * The classic priority chain: local override wins, then the remote value, then
     * [ConfigParam.defaultValue]. Used by [ConfigValues] unless a custom strategy is supplied.
     */
    public object Default : ValueResolutionStrategy {
        override fun <T : Any> resolve(
            param: ConfigParam<T>,
            localValue: ConfigValue<T>?,
            remoteValue: ConfigValue<T>?,
            defaultValue: ConfigValue<T>,
        ): ConfigValue<T> = localValue ?: remoteValue ?: defaultValue
    }
}
