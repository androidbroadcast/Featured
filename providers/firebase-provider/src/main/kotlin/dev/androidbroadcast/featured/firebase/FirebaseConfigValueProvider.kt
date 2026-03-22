package dev.androidbroadcast.featured.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.RemoteConfigValueProvider
import kotlinx.coroutines.tasks.await
import kotlin.reflect.KClass

/**
 * A [RemoteConfigValueProvider] backed by Firebase Remote Config.
 *
 * Reads values from [FirebaseRemoteConfig] and maps them to typed [ConfigValue] instances.
 * The value source reported in [ConfigValue.source] reflects the Firebase source constant
 * (`VALUE_SOURCE_REMOTE`, `VALUE_SOURCE_DEFAULT`, `VALUE_SOURCE_STATIC`).
 *
 * Out-of-the-box converters are registered for [String], [Boolean], [Int], [Long],
 * [Double], and [Float]. Custom converters can be added via [converters]:
 * ```kotlin
 * val provider = FirebaseConfigValueProvider()
 * provider.converters.put<MyEnum>(Converter { MyEnum.fromString(it.asString()) })
 * ```
 *
 * @param remoteConfig The [FirebaseRemoteConfig] instance to read from.
 *   Defaults to [FirebaseRemoteConfig.getInstance].
 */
public class FirebaseConfigValueProvider(
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : RemoteConfigValueProvider {
    /**
     * Mutable registry of type converters used to extract typed values from Firebase.
     *
     * Modify this property to add support for custom types before calling [get].
     */
    public val converters: Converters =
        Converters().apply {
            put<String>(Converter(FirebaseRemoteConfigValue::asString))
            put<Boolean>(Converter(FirebaseRemoteConfigValue::asBoolean))
            put<Int>(IntConverter())
            put<Long>(Converter(FirebaseRemoteConfigValue::asLong))
            put<Double>(Converter(FirebaseRemoteConfigValue::asDouble))
            put<Float>(FloatConverter())
        }

    /**
     * Returns the current Firebase Remote Config value for [param].
     *
     * Always returns a non-null [ConfigValue]; Firebase provides a value for every key
     * (falling back to static defaults when no remote or default value exists).
     *
     * @param param The configuration parameter to read.
     * @return A [ConfigValue] whose [ConfigValue.source] reflects the Firebase value origin.
     * @throws IllegalStateException if no converter is registered for the type of [param].
     */
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? =
        remoteConfig.getValue(param.key).let { remoteValue ->
            ConfigValue(
                value = remoteValue.asTyped(param.valueType),
                source = remoteValue.featureFlagValueSource(),
            )
        }

    private fun <T : Any> FirebaseRemoteConfigValue.asTyped(type: KClass<T>): T {
        converters[type]?.let { return it.convert(this) }

        // Auto-convert enums by name when no explicit converter is registered.
        @Suppress("UNCHECKED_CAST")
        if (type.java.isEnum) {
            val name = asString()
            val constants = requireNotNull(type.java.enumConstants) { "No enum constants for $type" }
            val match = constants.firstOrNull { (it as Enum<*>).name == name }
            return requireNotNull(match as T?) {
                "Unknown enum constant '$name' for $type. " +
                    "Valid values: ${constants.map { (it as Enum<*>).name }}"
            }
        }

        throw IllegalStateException("No converter registered for type: $type")
    }

    /**
     * Fetches the latest values from Firebase Remote Config and optionally activates them.
     *
     * @param activate When `true`, calls `fetchAndActivate()` so values become available
     *   immediately after this call. When `false`, only fetches without activating.
     * @throws FetchException if the Firebase fetch operation fails (e.g. network error,
     *   timeout, or service unavailability). The [FetchException.cause] holds the original
     *   exception for diagnostics. See [FetchException] for retry recommendations.
     */
    override suspend fun fetch(activate: Boolean) {
        val task =
            when (activate) {
                true -> remoteConfig.fetchAndActivate()
                false -> remoteConfig.fetch()
            }
        try {
            task.await()
        } catch (e: Exception) {
            throw FetchException("Firebase Remote Config fetch failed", e)
        }
    }
}

private fun FirebaseRemoteConfigValue.featureFlagValueSource(): ConfigValue.Source =
    when (this.source) {
        FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> ConfigValue.Source.REMOTE_DEFAULT
        FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> ConfigValue.Source.REMOTE
        FirebaseRemoteConfig.VALUE_SOURCE_STATIC -> ConfigValue.Source.DEFAULT
        else -> ConfigValue.Source.UNKNOWN
    }
