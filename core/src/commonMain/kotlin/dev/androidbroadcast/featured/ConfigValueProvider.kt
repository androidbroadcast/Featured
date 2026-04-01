package dev.androidbroadcast.featured

/**
 * An interface for providing configuration values from various sources.
 * It allows retrieval of configuration values based on parameters and supports
 * both local and remote configurations.
 *
 * Implementations of this interface can provide values from different sources,
 * such as local storage, remote servers, or a combination of both.
 */
public sealed interface ConfigValueProvider {
    /**
     * Retrieves the configuration value for the given parameter.
     * If the value is not available, returns null.
     *
     * @param param The configuration parameter to retrieve the value for.
     *
     * @return The value for the specified [param], or null if not available.
     */
    public suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>?
}

