package dev.androidbroadcast.featured.appmetrica

import com.yandex.varioqub.config.FetchError
import com.yandex.varioqub.config.OnFetchCompleteListener
import com.yandex.varioqub.config.Varioqub
import com.yandex.varioqub.config.VarioqubApi
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.InitializableConfigValueProvider
import dev.androidbroadcast.featured.RemoteConfigValueProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import com.yandex.varioqub.config.model.ConfigValue as VarioqubConfigValue

/**
 * A [RemoteConfigValueProvider] backed by Varioqub for AppMetrica.
 *
 * The app owns vendor startup. This provider does not activate AppMetrica and does not
 * initialize Varioqub. Reads use [VarioqubApi.getValue] and the typed `as*` converters.
 * [ConfigParam.defaultValue] is not pushed into the SDK and is not substituted when the
 * SDK returns a value.
 *
 * Out-of-the-box converters are registered for [String], [Boolean], [Int], [Long],
 * [Double], and [Float]. Custom converters can be added via [converters]:
 * ```kotlin
 * val provider = AppMetricaConfigValueProvider()
 * provider.converters.put<MyEnum>(Converter { MyEnum.valueOf(it.asString()) })
 * ```
 *
 * @param client The [VarioqubApi] instance to read from. Defaults to [Varioqub.getInstance].
 */
public class AppMetricaConfigValueProvider(
    private val client: VarioqubApi = Varioqub.getInstance(),
) : RemoteConfigValueProvider,
    InitializableConfigValueProvider {
    /**
     * Mutable registry of type converters used to extract typed values from Varioqub.
     *
     * Modify this property to add support for custom types before calling [get].
     * A later registration for the same type replaces the previous converter.
     */
    public val converters: Converters =
        Converters().apply {
            put<String>(Converter(VarioqubConfigValue::asString))
            put<Boolean>(Converter(VarioqubConfigValue::asBoolean))
            put<Int>(IntConverter())
            put<Long>(Converter(VarioqubConfigValue::asLong))
            put<Double>(Converter(VarioqubConfigValue::asDouble))
            put<Float>(FloatConverter())
        }

    /**
     * Returns the current activated Varioqub value for [param].
     *
     * Always returns a non-null [ConfigValue]. A missing SDK key is still a Varioqub value;
     * this implementation does not return null and does not substitute [ConfigParam.defaultValue].
     *
     * A registered converter wins over the built-in converter and over enum-name resolution.
     * An enum with no converter uses [VarioqubConfigValue.asString] as the constant name.
     *
     * @param param The configuration parameter to read.
     * @return A [ConfigValue] whose [ConfigValue.source] reflects the Varioqub source constant.
     * @throws IllegalArgumentException if a converter or enum name cannot parse the SDK value.
     * @throws IllegalStateException if no converter is registered for a non-enum type.
     */
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T> {
        val remoteValue =
            checkNotNull(client.getValue(param.key)) {
                "Varioqub returned no value for ${param.key}"
            }
        return ConfigValue(
            value = remoteValue.asTyped(param.valueType),
            source = remoteValue.featureFlagValueSource(),
        )
    }

    /**
     * Activates the configuration already stored by Varioqub.
     *
     * Calls [VarioqubApi.activateConfig] exactly once and suspends until its callback runs.
     * The callback has no error channel; invocation is success. This does not download a
     * new configuration.
     *
     * @throws AppMetricaConfigException if [VarioqubApi.activateConfig] throws a non-cancellation
     *   exception. The original exception is [AppMetricaConfigException.cause].
     * @throws kotlinx.coroutines.CancellationException if the call throws it, or if the waiting
     *   coroutine is cancelled. Cancellation is not wrapped and is not resumed later as
     *   [AppMetricaConfigException].
     */
    override suspend fun initialize() {
        try {
            suspendCancellableCoroutine { continuation: CancellableContinuation<Unit> ->
                client.activateConfig {
                    continuation.resumeIfActive()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppMetricaConfigException("Varioqub activateConfig failed", e)
        }
    }

    /**
     * Downloads a new Varioqub configuration and activates it only when [activate] is `true`.
     *
     * The interface default is `activate = true`. [fetch] with no argument uses that default.
     * `activate = false` returns on a successful download and does not call
     * [VarioqubApi.activateConfig]. A listener error, including a throttled request, throws
     * [AppMetricaConfigException] and does not activate. This provider does not retry and does
     * not sleep.
     *
     * @param activate When `true`, activate after a successful download and return when that
     *   activation callback runs. When `false`, return on a successful download.
     * @throws AppMetricaConfigException if the download reports an error, or if the client call
     *   throws a non-cancellation exception. The listener message and [FetchError] name are in
     *   the exception message. A listener failure has no throwable of its own; [cause] is an
     *   [IllegalStateException] whose message is the [FetchError] name.
     * @throws kotlinx.coroutines.CancellationException if a client call throws it, or if the
     *   waiting coroutine is cancelled. Cancellation is not wrapped.
     */
    override suspend fun fetch(activate: Boolean) {
        try {
            suspendCancellableCoroutine { continuation: CancellableContinuation<Unit> ->
                client.fetchConfig(
                    object : OnFetchCompleteListener {
                        override fun onSuccess() {
                            if (!activate) {
                                continuation.resumeIfActive()
                                return
                            }
                            if (!continuation.isActive) return
                            try {
                                client.activateConfig {
                                    continuation.resumeIfActive()
                                }
                            } catch (e: CancellationException) {
                                continuation.resumeFailure(e)
                            } catch (e: Exception) {
                                continuation.resumeFailure(
                                    AppMetricaConfigException("Varioqub activateConfig failed", e),
                                )
                            }
                        }

                        override fun onError(
                            message: String,
                            error: FetchError,
                        ) {
                            continuation.resumeFailure(
                                AppMetricaConfigException(
                                    "$message (${error.name})",
                                    IllegalStateException(error.name),
                                ),
                            )
                        }
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppMetricaConfigException) {
            throw e
        } catch (e: Exception) {
            throw AppMetricaConfigException("Varioqub fetchConfig failed", e)
        }
    }

    private fun <T : Any> VarioqubConfigValue.asTyped(type: KClass<T>): T {
        converters[type]?.let { return it.convert(this) }

        @Suppress("UNCHECKED_CAST")
        if (type.java.isEnum) {
            val name = asString()
            val constants =
                requireNotNull(type.java.enumConstants) { "No enum constants for $type" }
            val match = constants.firstOrNull { (it as Enum<*>).name == name }
            return requireNotNull(match) {
                "Unknown enum constant '$name' for $type. " +
                    "Valid values: ${constants.map { (it as Enum<*>).name }}"
            }
        }

        throw IllegalStateException("No converter registered for type: $type")
    }
}

private fun VarioqubConfigValue.featureFlagValueSource(): ConfigValue.Source =
    when (sourceType) {
        VarioqubSources.SOURCE_SERVER -> ConfigValue.Source.REMOTE
        VarioqubSources.SOURCE_INAPP_DEFAULT -> ConfigValue.Source.REMOTE_DEFAULT
        VarioqubSources.SOURCE_DEFAULT -> ConfigValue.Source.DEFAULT
        else -> ConfigValue.Source.UNKNOWN
    }

private fun CancellableContinuation<Unit>.resumeIfActive() {
    if (isActive) resume(Unit)
}

private fun CancellableContinuation<Unit>.resumeFailure(exception: Throwable) {
    if (isActive) resumeWithException(exception)
}
