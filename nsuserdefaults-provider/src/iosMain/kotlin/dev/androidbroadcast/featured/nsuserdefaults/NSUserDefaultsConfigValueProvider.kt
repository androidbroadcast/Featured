package dev.androidbroadcast.featured.nsuserdefaults

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import platform.Foundation.NSUserDefaults

/**
 * A [LocalConfigValueProvider] backed by iOS [NSUserDefaults].
 *
 * Values are persisted in the [NSUserDefaults] suite identified by [suiteName] (or the standard
 * user defaults when [suiteName] is `null`) and survive process restarts.
 *
 * Supported value types: [String], [Int], [Long], [Float], [Double], [Boolean].
 * Attempting to read or write an unsupported type throws [IllegalArgumentException].
 *
 * Active [observe] flows receive updates whenever [set], [resetOverride], or [clear] is called.
 *
 * ```kotlin
 * val provider = NSUserDefaultsConfigValueProvider(suiteName = "com.example.app.flags")
 * val configValues = ConfigValues(localProvider = provider)
 * ```
 *
 * @param suiteName The suite name passed to [NSUserDefaults]. `null` uses the standard user defaults.
 */
public class NSUserDefaultsConfigValueProvider(
    private val suiteName: String? = null,
) : LocalConfigValueProvider {

    private val defaults: NSUserDefaults = if (suiteName != null) {
        NSUserDefaults(suiteName = suiteName)
    } else {
        NSUserDefaults.standardUserDefaults
    }

    private val changedKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)

    /**
     * Returns the persisted value for [param], or `null` if it has not been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        val key = param.key
        // NSUserDefaults returns a default (0/false/"") when a key is absent, so we must
        // check object(forKey:) to distinguish "not set" from "set to default value".
        val rawObject = defaults.objectForKey(key) ?: return null

        val value: T = when (param.valueType) {
            Boolean::class -> defaults.boolForKey(key) as T
            Int::class -> defaults.integerForKey(key).toInt() as T
            Long::class -> defaults.integerForKey(key) as T
            Double::class -> defaults.doubleForKey(key) as T
            Float::class -> defaults.floatForKey(key) as T
            String::class -> (rawObject as? String ?: return null) as T
            else -> throw IllegalArgumentException("Unsupported type: ${param.valueType}")
        }
        return ConfigValue(value, ConfigValue.Source.LOCAL)
    }

    /**
     * Persists [value] as a local override for [param] and notifies active [observe] flows.
     *
     * @param param The configuration parameter to override.
     * @param value The value to persist.
     * @throws IllegalArgumentException if the type of [param] is not supported.
     */
    override suspend fun <T : Any> set(param: ConfigParam<T>, value: T) {
        val key = param.key
        when (value) {
            is Boolean -> defaults.setBool(value, forKey = key)
            is Int -> defaults.setInteger(value.toLong(), forKey = key)
            is Long -> defaults.setInteger(value, forKey = key)
            is Double -> defaults.setDouble(value, forKey = key)
            is Float -> defaults.setFloat(value, forKey = key)
            is String -> defaults.setObject(value, forKey = key)
            else -> throw IllegalArgumentException("Unsupported type: ${param.valueType}")
        }
        changedKeyFlow.tryEmit(key)
    }

    /**
     * Removes the persisted override for [param] and notifies active [observe] flows.
     *
     * After this call, [get] returns `null` and [ConfigValues] falls back to the remote
     * provider or [ConfigParam.defaultValue].
     *
     * @param param The configuration parameter whose override should be cleared.
     */
    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        defaults.removeObjectForKey(param.key)
        changedKeyFlow.tryEmit(param.key)
    }

    /**
     * Removes all persisted overrides by removing all keys from this provider's store.
     *
     * After this call, [get] returns `null` for every parameter that was previously set,
     * and [ConfigValues] falls back to the remote provider or [ConfigParam.defaultValue].
     */
    override suspend fun clear() {
        val dict = defaults.dictionaryRepresentation()
        for (key in dict.keys) {
            defaults.removeObjectForKey(key as String)
        }
    }

    /**
     * Returns a [Flow] that emits a [ConfigValue] for [param] on every change to its key.
     *
     * The flow emits the current persisted value immediately (skipping `null` if unset) and
     * then emits again whenever [set] or [resetOverride] is called for the same key.
     * Consecutive identical values are deduplicated via `distinctUntilChanged`.
     *
     * @param param The configuration parameter to observe.
     * @return A cold [Flow] that completes when the collector's scope is cancelled.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        flow {
            get(param)?.let { emit(it) }
            emitAll(
                changedKeyFlow
                    .filter { key -> key == param.key }
                    .mapNotNull { get(param) },
            )
        }.distinctUntilChanged()

    /**
     * Removes the entire NSUserDefaults suite, cleaning up all stored data.
     *
     * Primarily intended for use in tests to ensure a clean state between test cases.
     * Has no effect when [suiteName] is `null`.
     */
    public fun removeSuite() {
        if (suiteName != null) {
            NSUserDefaults.standardUserDefaults.removeSuiteNamed(suiteName)
        }
    }
}
