package dev.androidbroadcast.featured

/**
 * A snapshot of a single configuration parameter's resolved value together with its origin.
 *
 * [ConfigValues] produces [ConfigValue] instances when you call [ConfigValues.getValue] or
 * collect from [ConfigValues.observe]. The [source] property lets callers distinguish between
 * a hard-coded default, a locally persisted override, and a value fetched from a remote
 * configuration service.
 *
 * @param T The non-null type of the configuration value.
 * @property value The resolved value of the configuration parameter.
 * @property source Where this value originates; see [Source] for the full list of origins.
 */
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

/**
 * Transforms the [ConfigValue.value] while preserving the original [ConfigValue.source].
 *
 * Useful for mapping a raw configuration value to a domain type without losing provenance
 * information:
 * ```kotlin
 * val themeValue: ConfigValue<String> = configValues.getValue(themeParam)
 * val theme: ConfigValue<Theme> = themeValue.map { Theme.fromKey(it) }
 * ```
 *
 * @param T The original value type.
 * @param R The mapped value type.
 * @param transform Function applied to [ConfigValue.value].
 * @return A new [ConfigValue] with the transformed value and the same [ConfigValue.source].
 */
public inline fun <T : Any, R : Any> ConfigValue<T>.map(transform: (T) -> R): ConfigValue<R> =
    ConfigValue(value = transform(value), source = source)

/**
 * Executes [action] when [predicate] returns `true`, otherwise executes [elseAction].
 *
 * Both branches receive the full [ConfigValue] receiver, preserving source information.
 *
 * ```kotlin
 * configValues.getValue(featureParam).doIf(
 *     predicate = { it.value },
 *     action    = { enableFeature() },
 *     elseAction = { disableFeature() },
 * )
 * ```
 *
 * @param T The non-null value type.
 * @param predicate Condition evaluated against this [ConfigValue].
 * @param action Executed when [predicate] returns `true`.
 * @param elseAction Executed when [predicate] returns `false`; defaults to a no-op.
 */
public inline fun <T : Any> ConfigValue<T>.doIf(
    predicate: (ConfigValue<T>) -> Boolean,
    action: (ConfigValue<T>) -> Unit,
    elseAction: (ConfigValue<T>) -> Unit = {},
): Unit = if (predicate(this)) action(this) else elseAction(this)
