@file:Suppress("unused")

package dev.androidbroadcast.featured

import kotlin.reflect.KClass

public class ConfigParam<T : Any> @PublishedApi internal constructor(
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

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun toString(): String {
        return buildString {
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

public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    description: String? = null,
    category: String? = null,
    since: String? = null,
): ConfigParam<T> {
    return ConfigParam(
        key = key,
        defaultValue = defaultValue,
        valueType = T::class,
        description = description,
        category = category,
        since = since,
    )
}


