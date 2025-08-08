@file:Suppress("unused")

package dev.androidbroadcast.featured

import kotlin.reflect.KClass

public interface ConfigParam<T : Any> {
    /**
     * The unique key for the configuration parameter.
     * This key is used to identify the parameter across different providers.
     */
    public val key: String

    /**
     * The default value for the configuration parameter.
     * This value is used when no other value is available from providers.
     */
    public val defaultValue: T

    /**
     * The type of the value for the configuration parameter.
     * This is used to ensure type safety when retrieving the value.
     */
    public val valueType: KClass<T>

    /**
     * An optional description for the configuration parameter.
     * This can be used to provide additional context or information about the parameter.
     */
    public val description: String?
        get() = null

    /**
     * Category or group name for organizing related parameters.
     * Used for UI grouping and organization.
     */
    public val category: String?
        get() = null

    /**
     * Version when this parameter was introduced.
     * Useful for tracking parameter lifecycle.
     */
    public val sinceVersion: String?
        get() = null
}

public class SimpleConfigParam<T : Any>(
    override val key: String,
    override val defaultValue: T,
    override val valueType: KClass<T>,
    override val description: String? = null,
    override val category: String? = null,
    override val sinceVersion: String? = null,
) : ConfigParam<T> {

    override fun toString(): String {
        return "ConfigParam(" +
                "key='$key'" + ", " +
                "defaultValue=$defaultValue" + ", " +
                "category=$category" + ", " +
                "sinceVersion=$sinceVersion" + ", " +
                "description=$description" +
                ")"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ConfigParam<*>
        return key == other.key &&
                defaultValue == other.defaultValue &&
                valueType == other.valueType &&
                description == other.description &&
                category == other.category &&
                sinceVersion == other.sinceVersion
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}

public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    description: String? = null,
    category: String? = null,
    sinceVersion: String? = null,
): ConfigParam<T> {
    return SimpleConfigParam(
        key = key,
        defaultValue = defaultValue,
        valueType = T::class,
        description = description,
        category = category,
        sinceVersion = sinceVersion,
    )
}
