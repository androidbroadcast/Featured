package dev.androidbroadcast.featured.configcat

import com.configcat.ConfigCatClient
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.RemoteConfigValueProvider
import kotlin.reflect.KClass

/**
 * A [RemoteConfigValueProvider] backed by the ConfigCat KMP SDK.
 *
 * Reads values from [ConfigCatClient] and maps them to typed [ConfigValue] instances.
 * Supported types out of the box: [Boolean], [String], [Int], [Long], [Double], [Float].
 *
 * For [Long], the value is read as [Double] and converted via [Double.toLong].
 * For [Float], the value is read as [Double] and converted via [Double.toFloat].
 *
 * Custom type mapping can be done by wrapping this provider and post-processing
 * the returned [ConfigValue].
 *
 * Usage:
 * ```kotlin
 * val client = ConfigCatClient(sdkKey = "YOUR_SDK_KEY")
 * val provider = ConfigCatConfigValueProvider(client)
 *
 * val configValues = ConfigValues(remoteProvider = provider)
 * ```
 *
 * @param client The [ConfigCatClient] instance used to retrieve feature flag values.
 */
public class ConfigCatConfigValueProvider(
    private val client: ConfigCatClient,
) : RemoteConfigValueProvider {
    /**
     * Returns the ConfigCat value for [param], or `null` if the key is not found in ConfigCat.
     *
     * The value source is always [ConfigValue.Source.REMOTE] when a value is found.
     *
     * @param param The configuration parameter to read.
     * @return A [ConfigValue] wrapping the ConfigCat value, or `null` if not found.
     * @throws IllegalArgumentException if the value type is not supported.
     */
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        val value = getTypedValue(param.key, param.valueType) ?: return null
        return ConfigValue(value = value, source = ConfigValue.Source.REMOTE)
    }

    /**
     * Fetches the latest values from ConfigCat by calling [ConfigCatClient.forceRefresh].
     *
     * The [activate] parameter is accepted for API compatibility but ConfigCat always
     * activates refreshed values immediately, so it has no additional effect.
     *
     * @param activate Ignored — ConfigCat activates values on every [forceRefresh] call.
     */
    override suspend fun fetch(activate: Boolean) {
        client.forceRefresh()
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> getTypedValue(
        key: String,
        type: KClass<T>,
    ): T? =
        when (type) {
            Boolean::class -> {
                client.getAnyValue(key, null, null)?.let { it as? Boolean } as T?
            }

            String::class -> {
                client.getAnyValue(key, null, null)?.let { it as? String } as T?
            }

            Int::class -> {
                client
                    .getAnyValue(key, null, null)
                    ?.let { it as? Number }
                    ?.toInt() as T?
            }

            Long::class -> {
                client
                    .getAnyValue(key, null, null)
                    ?.let { it as? Number }
                    ?.toLong() as T?
            }

            Double::class -> {
                client
                    .getAnyValue(key, null, null)
                    ?.let { it as? Number }
                    ?.toDouble() as T?
            }

            Float::class -> {
                client
                    .getAnyValue(key, null, null)
                    ?.let { it as? Number }
                    ?.toFloat() as T?
            }

            else -> {
                throw IllegalArgumentException(
                    "Unsupported type: $type. " +
                        "Supported types are Boolean, String, Int, Long, Double, Float.",
                )
            }
        }
}
