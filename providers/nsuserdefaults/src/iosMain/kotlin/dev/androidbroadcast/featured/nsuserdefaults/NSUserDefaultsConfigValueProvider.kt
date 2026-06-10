package dev.androidbroadcast.featured.nsuserdefaults

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSArray
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
 * After [clear], observers for previously-set keys emit [ConfigValue.Source.DEFAULT].
 *
 * **Written-key index:** to scope [clear] to provider-owned keys only (avoiding destruction of
 * foreign or system keys in the same suite), every [set] / [resetOverride]-write atomically adds
 * or removes the param key in a persistent string-array index stored under [RESERVED_INDEX_KEY].
 * [clear] reads the index, removes only those keys, and emits change signals for each one.
 * The index may desync on crash between writes; this is self-healing — [get] never consults the
 * index and always reads directly from [NSUserDefaults]. Setting or getting the reserved key
 * itself throws [IllegalArgumentException].
 *
 * **Concurrency:** in-process index read-modify-write operations (in [addToIndex], [removeFromIndex],
 * and [clear]) are serialized via an internal [Mutex]. Cross-process access or crash-interrupted
 * writes may still desync the index; the self-healing invariant above covers that case.
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
    private val defaults: NSUserDefaults =
        if (suiteName != null) {
            NSUserDefaults(suiteName = suiteName)
        } else {
            NSUserDefaults.standardUserDefaults
        }

    private val changedKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)
    private val indexMutex = Mutex()

    /**
     * Returns the persisted value for [param], or `null` if it has not been set.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     * @throws IllegalArgumentException if [param] key equals [RESERVED_INDEX_KEY] or if the
     *   type of [param] is not supported.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        requireNotReservedKey(param.key)
        val key = param.key
        // NSUserDefaults returns a default (0/false/"") when a key is absent, so we must
        // check object(forKey:) to distinguish "not set" from "set to default value".
        val rawObject = defaults.objectForKey(key) ?: return null

        val value: T =
            when (param.valueType) {
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
     * Also records [param] key in the internal written-key index so that [clear] can scope
     * its deletion to provider-owned keys.
     *
     * @param param The configuration parameter to override.
     * @param value The value to persist.
     * @throws IllegalArgumentException if [param] key equals [RESERVED_INDEX_KEY] or if the
     *   type of [param] is not supported.
     */
    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        requireNotReservedKey(param.key)
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
        addToIndex(key)
        changedKeyFlow.tryEmit(key)
    }

    /**
     * Removes the persisted override for [param] and notifies active [observe] flows.
     *
     * Also removes [param] key from the internal written-key index.
     *
     * After this call, [get] returns `null` and [ConfigValues] falls back to the remote
     * provider or [ConfigParam.defaultValue].
     *
     * @param param The configuration parameter whose override should be cleared.
     */
    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        requireNotReservedKey(param.key)
        defaults.removeObjectForKey(param.key)
        removeFromIndex(param.key)
        changedKeyFlow.tryEmit(param.key)
    }

    /**
     * Removes all provider-owned keys and notifies active [observe] flows for each one.
     *
     * Only keys tracked in the internal written-key index (stored under [RESERVED_INDEX_KEY])
     * are deleted — foreign keys written directly into the same NSUserDefaults suite by other
     * code are not touched. The index itself is also deleted.
     *
     * After this call, [get] returns `null` for every parameter that was previously set via
     * this provider, and active [observe] flows emit [ConfigValue.Source.DEFAULT].
     *
     * **Note:** NSUserDefaults is not transactional. A crash between the value write and the
     * index write in [set] can leave the index out of sync; the self-healing invariant is that
     * [get] never reads the index and always queries NSUserDefaults directly.
     */
    override suspend fun clear() {
        val writtenKeys =
            indexMutex.withLock {
                val keys = readIndex()
                for (key in keys) {
                    defaults.removeObjectForKey(key)
                }
                defaults.removeObjectForKey(RESERVED_INDEX_KEY)
                keys
            }
        for (key in writtenKeys) {
            changedKeyFlow.tryEmit(key)
        }
    }

    /**
     * Returns a [Flow] that emits a [ConfigValue] for [param] on every change to its key.
     *
     * Conforms to [LocalConfigValueProvider.observe] contract: the flow always emits immediately
     * upon collection — [ConfigValue.Source.LOCAL] when a value is persisted,
     * [ConfigValue.Source.DEFAULT] wrapping [ConfigParam.defaultValue] otherwise. It then emits
     * again whenever [set], [resetOverride], or [clear] is called for the same key. Consecutive
     * identical values are deduplicated via `distinctUntilChanged`.
     *
     * @param param The configuration parameter to observe.
     * @return A cold [Flow] that completes when the collector's scope is cancelled.
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        flow {
            // Wrap initial get in the same error-isolation as the reactive path so that
            // invalid param keys (e.g. the reserved index key) emit DEFAULT rather than
            // terminating the flow with an exception.
            val initial =
                try {
                    get(param) ?: ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    ConfigValue(param.defaultValue, ConfigValue.Source.DEFAULT)
                }
            emit(initial)
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
        }.distinctUntilChanged()

    /**
     * Removes the entire NSUserDefaults suite, cleaning up all stored data.
     *
     * Primarily intended for use in tests to ensure a clean state between test cases.
     * Has no effect when [suiteName] is `null`.
     */
    internal fun removeSuite() {
        if (suiteName != null) {
            NSUserDefaults.standardUserDefaults.removeSuiteNamed(suiteName)
        }
    }

    // --- Written-key index helpers ---

    private fun readIndex(): List<String> {
        @Suppress("UNCHECKED_CAST")
        val array = defaults.arrayForKey(RESERVED_INDEX_KEY) as? NSArray ?: return emptyList()
        return (0 until array.count.toInt()).mapNotNull { i ->
            array.objectAtIndex(i.toULong()) as? String
        }
    }

    private suspend fun addToIndex(key: String) {
        indexMutex.withLock {
            val current = readIndex().toMutableList()
            if (!current.contains(key)) {
                current.add(key)
            }
            defaults.setObject(current, forKey = RESERVED_INDEX_KEY)
        }
    }

    private suspend fun removeFromIndex(key: String) {
        indexMutex.withLock {
            val current = readIndex().toMutableList()
            current.remove(key)
            defaults.setObject(current, forKey = RESERVED_INDEX_KEY)
        }
    }

    private fun requireNotReservedKey(key: String) {
        require(key != RESERVED_INDEX_KEY) {
            "Key '$key' is reserved for internal use by NSUserDefaultsConfigValueProvider " +
                "and must not be used as a ConfigParam key."
        }
    }

    public companion object {
        /**
         * NSUserDefaults key used to store the index of keys written by this provider.
         *
         * This key is reserved and must not be used as a [ConfigParam] key. Reading or writing
         * a [ConfigParam] whose key equals this constant throws [IllegalArgumentException].
         */
        public const val RESERVED_INDEX_KEY: String = "dev.androidbroadcast.featured.written_keys"
    }
}
