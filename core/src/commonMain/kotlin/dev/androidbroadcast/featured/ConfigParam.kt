@file:Suppress("unused")

package dev.androidbroadcast.featured

import kotlin.reflect.KClass

/**
 * Declares a named, typed configuration key with a default value.
 *
 * A [ConfigParam] is a descriptor — it does not hold a live value itself. Pass it to
 * [ConfigValues] to read, observe, or override the corresponding runtime value.
 *
 * Instances are considered equal when all constructor properties are equal. The hash code
 * is derived solely from [key] so that params can be used as map keys with predictable
 * behaviour.
 *
 * Prefer the inline factory function [ConfigParam] over the primary constructor to avoid
 * passing an explicit [kotlin.reflect.KClass] argument:
 * ```kotlin
 * val darkMode = ConfigParam(key = "dark_mode", defaultValue = false)
 * ```
 *
 * @param T The non-null type of the configuration value.
 */
public class ConfigParam<T : Any>
    @PublishedApi
    internal constructor(
        /**
         * The unique key for the configuration parameter.
         * This key is used to identify the parameter across different providers.
         */
        public val key: String,
        /**
         * The default value for the configuration parameter.
         * This value is used when no other value is available from providers.
         */
        public val defaultValue: T,
        /**
         * The type of the value for the configuration parameter.
         * This is used to ensure type safety when retrieving the value.
         */
        public val valueType: KClass<T>,
        /**
         * An optional description for the configuration parameter.
         * This can be used to provide additional context or information about the parameter.
         */
        public val description: String? = null,
        /**
         * Category or group name for organizing related parameters.
         * Used for UI grouping and organization.
         */
        public val category: String? = null,
        /**
         * Version or date when this parameter was introduced.
         * Useful for tracking parameter lifecycle.
         */
        public val since: String? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ConfigParam<*>

            return key == other.key &&
                defaultValue == other.defaultValue &&
                valueType == other.valueType &&
                description == other.description &&
                category == other.category &&
                since == other.since
        }

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String =
            buildString {
                append("ConfigParam")
                append('(')

                appendIfPresent(key = "key", key, addComma = false)
                appendIfPresent(key = "defaultValue", defaultValue)
                appendIfPresent(key = "description", description)
                appendIfPresent(key = "category", category)
                appendIfPresent(key = "since", since)

                append(')')
            }
    }

private fun StringBuilder.appendIfPresent(
    key: String,
    value: Any?,
    addComma: Boolean = true,
): StringBuilder {
    if (value != null) {
        if (addComma) append(',')
        append(key)
        append('=')
        append('\'')
        append(value)
        append('\'')
    }
    return this
}

/**
 * Creates a [ConfigParam] without requiring an explicit [kotlin.reflect.KClass] argument.
 *
 * The type parameter [T] is reified at the call site, so the compiler fills in [valueType]
 * automatically.
 *
 * ```kotlin
 * val maxRetries = ConfigParam(key = "max_retries", defaultValue = 3)
 * val theme = ConfigParam(key = "theme", defaultValue = "light", description = "App colour theme")
 * ```
 *
 * @param T The non-null type of the configuration value.
 * @param key Unique identifier used to look up this parameter in providers.
 * @param defaultValue Value returned when no provider supplies a value for this key.
 * @param description Optional human-readable explanation shown in debug UIs.
 * @param category Optional group name used to organise related params in debug UIs.
 * @param since Optional version or date string indicating when this param was introduced.
 * @return A [ConfigParam] instance typed to [T].
 */
public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    description: String? = null,
    category: String? = null,
    since: String? = null,
): ConfigParam<T> =
    ConfigParam(
        key = key,
        defaultValue = defaultValue,
        valueType = T::class,
        description = description,
        category = category,
        since = since,
    )
