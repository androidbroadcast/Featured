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
 * ### Requiring both sides true
 *
 * A feature is enabled only when both sides are explicitly `true` by passing a strategy to the
 * existing [ConfigValues] `resolutionStrategy` parameter. The library does not AND flags itself.
 * One generated flag is enough: [ConfigValues.getValue] already reads the local provider and the
 * remote provider for that same [ConfigParam], then calls [resolve]. The local side is the value
 * that provider already stores, or the value written by [ConfigValues.override]. The remote side
 * is the remote provider's value for the same key. Declare the flag once.
 *
 * Two generated flags with the same name are not the AND mechanism. Same-name isolation is a
 * separate task (GitHub #294). Do not declare the key in both `localFlags` and `remoteFlags`:
 * the build rejects that duplicate. Do not AND two accessors. If the guarded client code must
 * remain in release binaries, do not declare that one flag as a `localFlags` boolean with
 * `default = false`: those flags are release-stripped.
 *
 * Both providers must be configured for a flag that uses this recipe. A missing provider is a
 * null candidate, and the recipe then stays `false`. [ConfigValues] already requires at least
 * one provider; this AND needs both.
 *
 * Apply it only to selected boolean keys. Detect a boolean param with
 * `defaultValue.value is Boolean`. Do not treat [defaultValue] as the client gate, and do not
 * AND it with the remote value: a flag whose default is `false` can still become `true` when
 * both provider values are `true`. A missing, failed, `false`, or otherwise non-true side is
 * not `true`. Do not fall back to `local ?: remote ?: default` for a selected key.
 *
 * The combined boolean, `true` or `false`, is `ConfigValue(enabled, ConfigValue.Source.UNKNOWN)`.
 * The strategy chooses [ConfigValue.Source.UNKNOWN]. The library does not stamp a combined
 * source. [ConfigValues.getValue] writes every non-[ConfigValue.Source.DEFAULT] source into the
 * snapshot, so [ConfigValues.getValueCached] sees [ConfigValue.Source.UNKNOWN] and the combined
 * value. [ConfigValues.observe] re-resolves through the same strategy, so a combined emission
 * is also [ConfigValue.Source.UNKNOWN]. A remote-only `true` must not be reported as
 * [ConfigValue.Source.REMOTE]. A combined `false` is not [ConfigValue.Source.DEFAULT] and is
 * not the surviving side's source.
 *
 * Any other key, and any non-boolean param, delegates to [Default] and returns that candidate
 * unchanged, keeping its source: [ConfigValue.Source.LOCAL], [ConfigValue.Source.REMOTE],
 * [ConfigValue.Source.REMOTE_DEFAULT], or [ConfigValue.Source.DEFAULT]. A `DEFAULT` result is
 * not written to the snapshot.
 *
 * [resolve] receives [param], so the strategy does not have to be global. Prefer this per-param
 * form when one [ConfigValues] holds mixed flags. The other existing option is a separate
 * [ConfigValues] constructed with this strategy, while other instances keep [Default].
 *
 * ```kotlin
 * val andGatedKeys = setOf("new_checkout")
 *
 * val requireBothExplicitlyTrue = object : ValueResolutionStrategy {
 *     override fun <T : Any> resolve(
 *         param: ConfigParam<T>,
 *         localValue: ConfigValue<T>?,
 *         remoteValue: ConfigValue<T>?,
 *         defaultValue: ConfigValue<T>,
 *     ): ConfigValue<T> {
 *         if (defaultValue.value is Boolean && param.key in andGatedKeys) {
 *             val enabled = localValue?.value == true && remoteValue?.value == true
 *             @Suppress("UNCHECKED_CAST")
 *             return ConfigValue(enabled as T, ConfigValue.Source.UNKNOWN)
 *         }
 *         return ValueResolutionStrategy.Default.resolve(
 *             param,
 *             localValue,
 *             remoteValue,
 *             defaultValue,
 *         )
 *     }
 * }
 *
 * val localProvider = InMemoryConfigValueProvider()
 * val remoteProvider = FirebaseConfigValueProvider()
 *
 * // Recommended when one ConfigValues holds mixed flags.
 * val configValues = ConfigValues(
 *     localProvider = localProvider,
 *     remoteProvider = remoteProvider,
 *     resolutionStrategy = requireBothExplicitlyTrue,
 * )
 *
 * // Other instances over the same providers can keep Default.
 * val classic = ConfigValues(
 *     localProvider = localProvider,
 *     remoteProvider = remoteProvider,
 * )
 * ```
 *
 * ### Examples
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
