package dev.androidbroadcast.featured.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.RemoteConfigValueProvider
import kotlinx.coroutines.tasks.await
import kotlin.reflect.KClass

public class FirebaseConfigValueProvider(
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : RemoteConfigValueProvider {

    public val converters: Converters = Converters().apply {
        put<String>(Converter(FirebaseRemoteConfigValue::asString))
        put<Boolean>(Converter(FirebaseRemoteConfigValue::asBoolean))
        put<Int>(IntConverter())
        put<Long>(Converter(FirebaseRemoteConfigValue::asLong))
        put<Double>(Converter(FirebaseRemoteConfigValue::asDouble))
        put<Float>(FloatConverter())
    }

    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        return remoteConfig.getValue(param.key).let { remoteValue ->
            ConfigValue(
                value = remoteValue.asTyped(param.valueType),
                source = remoteValue.featureFlagValueSource()
            )
        }
    }

    private fun <T : Any> FirebaseRemoteConfigValue.asTyped(type: KClass<T>): T {
        val converter = requireNotNull(converters[type]) {
            "No converter registered for type: $type"
        }
        return converter.convert(this)
    }

    override suspend fun fetch(activate: Boolean) {
        val task = when (activate) {
            true -> remoteConfig.fetchAndActivate()
            false -> remoteConfig.fetch()
        }
        task.await()
    }
}

private fun FirebaseRemoteConfigValue.featureFlagValueSource(
): ConfigValue.Source = when (this.source) {
    FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> ConfigValue.Source.REMOTE_DEFAULT
    FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> ConfigValue.Source.REMOTE
    FirebaseRemoteConfig.VALUE_SOURCE_STATIC -> ConfigValue.Source.DEFAULT
    else -> ConfigValue.Source.UNKNOWN
}
