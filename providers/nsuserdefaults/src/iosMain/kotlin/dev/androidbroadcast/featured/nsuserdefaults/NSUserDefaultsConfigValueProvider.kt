package dev.androidbroadcast.featured.nsuserdefaults

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.TypeConverter
import dev.androidbroadcast.featured.TypeConverters
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
import kotlin.reflect.KClass

/**
 * A [LocalConfigValueProvider] backed by iOS [NSUserDefaults].
 *
 * Values are persisted in the [NSUserDefaults] suite identified by [suiteName] (or the standard
 * user defaults when [suiteName] is `null`) and survive process restarts.
 *
 * Supported value types: [String], [Int], [Long], [Float], [Double], [Boolean].
 * Custom types (e.g. enums) are supported by registering a [TypeConverter] via [registerConverter]
 * before the first [get] or [set] call for that type. Attempting to read or write a type with no
 * built-in support and no registered converter throws [IllegalArgumentException].
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
 * **Concurrency:** in-process [set] / [resetOverride] serialize their NSUserDefaults value-write
 * together with the corresponding index update under [indexMutex], the same mutex used by [clear].
 * This closes the window where a key written by [set] could escape [clear]'s index snapshot.
 * Note that NSUserDefaults [setObject] has no error return — a silent platform failure can desync
 * the index without a crash; [get] remains unaffected as it reads directly from NSUserDefaults.
 * Cross-process access or crash-interrupted writes may still desync the index; the self-healing
 * invariant above covers that case.
 *
 * ```kotlin
 * val provider = NSUserDefaultsConfigValueProvider(suiteName = "com.example.app.flags").apply {
 *     registerConverter(enumConverter<CheckoutVariant>())
 * }
 * val configValues = ConfigValues(localProvider = provider)
 * ```
 *
 * Calls are expected off the main thread.
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

    private val typeConverters = TypeConverters()
    private val changedKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = Int.MAX_VALUE)
    private val indexMutex = Mutex()

    /**
     * Registers a [TypeConverter] for [klass], enabling [set] and [get] for custom types
     * (e.g. enums) that are serialized as strings in NSUserDefaults.
     *
     * Must be called before any [set] or [get] call for the corresponding type.
     * Prefer the inline overload [registerConverter] to avoid passing [KClass] explicitly.
     *
     * @param T The non-null type to register.
     * @param klass The [KClass] of the type.
     * @param converter The [TypeConverter] that serializes/deserializes [T] as a [String].
     */
    public fun <T : Any> registerConverter(
        klass: KClass<T>,
        converter: TypeConverter<T>,
    ) {
        typeConverters[klass] = converter
    }

    /**
     * Returns the persisted value for [param], or `null` if it has not been set.
     *
     * For custom types (e.g. enums), the stored [String] is decoded via the registered
     * [TypeConverter]. Returns `null` if no value has been set, regardless of type.
     *
     * @param param The configuration parameter to look up.
     * @return A [ConfigValue] with [ConfigValue.Source.LOCAL], or `null` if not present.
     * @throws IllegalArgumentException if [param] key equals [RESERVED_INDEX_KEY] or if the
     *   type of [param] is not supported and no converter is registered.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        requireNotReservedKey(param.key)
        val key = param.key
        // NSUserDefaults returns a default (0/false/"") when a key is absent, so we must
        // check object(forKey:) to distinguish "not set" from "set to default value".
        val rawObject = defaults.objectForKey(key) ?: return null

        val converter = typeConverters.get(param.valueType)
        if (converter != null) {
            val raw = rawObject as? String ?: return null
            return ConfigValue(converter.fromString(raw), ConfigValue.Source.LOCAL)
        }

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
     * For custom types (e.g. enums), the value is serialized to a [String] via the registered
     * [TypeConverter] and stored as a string object. Also records [param] key in the internal
     * written-key index so that [clear] can scope its deletion to provider-owned keys.
     *
     * @param param The configuration parameter to override.
     * @param value The value to persist.
     * @throws IllegalArgumentException if [param] key equals [RESERVED_INDEX_KEY], if the
     *   type of [param] is not supported, or if no converter is registered for a custom type.
     */
    override suspend fun <T : Any> set(
        param: ConfigParam<T>,
        value: T,
    ) {
        requireNotReservedKey(param.key)
        val key = param.key
        // Value-write and index-update share the mutex with clear() to prevent a race where
        // a freshly-written key escapes clear()'s index snapshot.
        indexMutex.withLock {
            val converter = typeConverters.get(param.valueType)
            if (converter != null) {
                defaults.setObject(converter.toString(value), forKey = key)
            } else {
                when (value) {
                    is Boolean -> defaults.setBool(value, forKey = key)
                    is Int -> defaults.setInteger(value.toLong(), forKey = key)
                    is Long -> defaults.setInteger(value, forKey = key)
                    is Double -> defaults.setDouble(value, forKey = key)
                    is Float -> defaults.setFloat(value, forKey = key)
                    is String -> defaults.setObject(value, forKey = key)
                    else -> throw IllegalArgumentException("Unsupported type: ${param.valueType}")
                }
            }
            val current = readIndex().toMutableList()
            if (!current.contains(key)) {
                current.add(key)
            }
            defaults.setObject(current, forKey = RESERVED_INDEX_KEY)
        }
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
        val key = param.key
        // Remove and index-update share the mutex with set()/clear() to close the in-process race.
        indexMutex.withLock {
            defaults.removeObjectForKey(key)
            val current = readIndex().toMutableList()
            current.remove(key)
            defaults.setObject(current, forKey = RESERVED_INDEX_KEY)
        }
        changedKeyFlow.tryEmit(key)
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
                keys.forEach { key -> defaults.removeObjectForKey(key) }
                defaults.removeObjectForKey(RESERVED_INDEX_KEY)
                keys
            }
        writtenKeys.forEach { key -> changedKeyFlow.tryEmit(key) }
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
     * Storage or converter errors during the initial read and during reactive updates are caught
     * and surfaced as a [ConfigValue.Source.DEFAULT]-sourced emission; the flow never terminates
     * and the error is not propagated to the collector.
     *
     * The reserved-key guard ([RESERVED_INDEX_KEY]) is checked eagerly at construction time so
     * that passing an invalid [param] throws at the call site rather than being swallowed inside
     * the flow's error-isolation handler.
     *
     * @param param The configuration parameter to observe.
     * @return A cold [Flow] that completes when the collector's scope is cancelled.
     * @throws IllegalArgumentException if [param] key equals [RESERVED_INDEX_KEY].
     */
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        requireNotReservedKey(param.key)
        return flow {
            // Wrap initial get in the same error-isolation as the reactive path so that
            // other invalid param keys emit DEFAULT rather than terminating the flow with
            // an exception.
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
    }

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

/**
 * Registers a [TypeConverter] for the reified type [T] on this [NSUserDefaultsConfigValueProvider],
 * enabling [NSUserDefaultsConfigValueProvider.set] and [NSUserDefaultsConfigValueProvider.get] for
 * custom types (e.g. enums) that are serialized as strings in NSUserDefaults.
 *
 * Inline convenience wrapper that avoids passing [kotlin.reflect.KClass] explicitly:
 * ```kotlin
 * provider.registerConverter(enumConverter<CheckoutVariant>())
 * ```
 *
 * @param T The non-null custom type to register.
 * @param converter The [TypeConverter] that serializes/deserializes [T] as a [String].
 */
public inline fun <reified T : Any> NSUserDefaultsConfigValueProvider.registerConverter(converter: TypeConverter<T>) {
    registerConverter(T::class, converter)
}
