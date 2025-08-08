package dev.androidbroadcast.featured

public data class ConfigValue<T : Any>(
    /**
     * The value of the configuration parameter.
     * This is the actual value that was fetched from a provider.
     */
    val value: T,

    /**
     * The source of the value.
     * This indicates where the value was fetched from, such as a default value, remote source, or local storage.
     */
    val source: Source,
) {

    /**
     * Represents the source of the configuration value.
     * This enum defines the possible origins of the value, such as default values, remote sources,
     * local storage, or unknown sources.
     */
    public enum class Source {
        /**
         * The value was fetched from the [ConfigParam.defaultValue].
         */
        DEFAULT,

        /**
         * The value was fetched from a remote source, such as Firebase Remote Config.
         */
        REMOTE,

        /**
         * The value was fetched from a remote source, but it was the default value.
         */
        REMOTE_DEFAULT,

        /**
         * The value was fetched from the local storage.
         */
        LOCAL,

        /**
         * The value was fetched from an unknown source.
         */
        UNKNOWN,
    }
}

public inline fun <T : Any, R : Any> ConfigValue<T>.map(
    transform: (T) -> R,
): ConfigValue<R> {
    return ConfigValue(value = transform(value), source = source)
}


public inline fun <T : Any> ConfigValue<T>.doIf(
    predicate: (ConfigValue<T>) -> Boolean,
    action: (ConfigValue<T>) -> Unit,
    elseAction: (ConfigValue<T>) -> Unit = {},
) {
    return if (predicate(this)) action(this) else elseAction(this)
}
