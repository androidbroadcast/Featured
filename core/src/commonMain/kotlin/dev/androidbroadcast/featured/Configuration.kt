package dev.androidbroadcast.featured

/**
 * Pre-filled values for [ConfigParam] that will be used instead of [ConfigParam.defaultValue]
 */
public interface Configuration {

    public fun <T : Any> get(param: ConfigParam<T>): T = param.defaultValue
}

internal object EmptyConfiguration: Configuration

/**
 * Create new [Configuration]
 */
public fun Configuration(body: MutableConfiguration.() -> Unit): Configuration {
    return MutableConfigurationImpl().apply(body).copy()
}

internal open class ConfigurationImpl(
    protected val values: MutableMap<ConfigParam<*>, Any> = mutableMapOf()
) : Configuration {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(param: ConfigParam<T>): T {
        return values.getOrElse(param) { param.defaultValue } as T
    }

    fun copy(): Configuration {
        return ConfigurationImpl(values.toMutableMap())
    }
}

public interface MutableConfiguration : Configuration {

    public operator fun <T : Any> set(param: ConfigParam<T>, value: T)
}

internal class MutableConfigurationImpl() : ConfigurationImpl(), MutableConfiguration {

    @Suppress("UNCHECKED_CAST")
    override operator fun <T : Any> set(param: ConfigParam<T>, value: T) {
        values[param] = value
    }
}