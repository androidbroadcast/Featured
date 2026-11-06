@file:Suppress("unused")
@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package dev.androidbroadcast.featured

import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Which providers [ConfigValues] may read for a [ConfigParam], and which local slot it occupies.
 *
 * The public [ConfigParam.key] is not rewritten. Remote providers are always asked for that public
 * key. Local storage and the [ConfigValues] snapshot use [ConfigParam.localStorageKey], which is
 * this scope's name, a colon, and the public key.
 */
public enum class FlagScope {
    /**
     * Read the local provider, then this param's default. The remote provider is not called.
     */
    LOCAL,

    /**
     * Read the remote provider, then this param's default. Resolution does not read the local
     * provider. A stored override of this param is not a resolution candidate.
     */
    REMOTE,

    /**
     * Read local, then remote, then this param's default. This is the scope of a [ConfigParam]
     * constructed without an explicit scope, including already compiled callers.
     */
    ANY,
}

/**
 * Declares a named, typed configuration key with a default value.
 *
 * A [ConfigParam] is a descriptor — it does not hold a live value itself. Pass it to
 * [ConfigValues] to read, observe, or override the corresponding runtime value.
 *
 * Instances are considered equal when all constructor properties are equal, including [scope].
 * The hash code is derived solely from [key] so that params can be used as map keys with
 * predictable behaviour. The snapshot does not use this instance as its map key; it uses
 * [localStorageKey].
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
         * The public name of the configuration parameter.
         *
         * Remote providers look up this string. Local storage and the snapshot do not: they use
         * [localStorageKey]. Construction does not rewrite [key]. Two params may share a [key]
         * when their [scope] differs; within one scope the same key is one slot.
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
         * Which providers may supply a value, and which local slot this param occupies.
         *
         * Omitted by the constructor and factory that predate this property; those call sites
         * are [FlagScope.ANY].
         */
        public val scope: FlagScope,
        /**
         * An optional description for the configuration parameter.
         * This can be used to provide additional context or information about the parameter.
         */
        @property:ObjCName("summary")
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
        /**
         * All declared constants of the enum type, or `null` for non-enum params.
         * Populated by the code generator via `enumValues<T>().toList()`; not set for hand-written params.
         */
        public val enumConstants: List<T>? = null,
    ) {
        /**
         * Constructor without [scope]. Assigns [FlagScope.ANY].
         *
         * Already compiled callers of the pre-scope signature link here. Do not add [FlagScope]
         * to this parameter list.
         */
        @PublishedApi
        internal constructor(
            key: String,
            defaultValue: T,
            valueType: KClass<T>,
            description: String?,
            category: String?,
            since: String?,
            enumConstants: List<T>?,
        ) : this(
            key = key,
            defaultValue = defaultValue,
            valueType = valueType,
            scope = FlagScope.ANY,
            description = description,
            category = category,
            since = since,
            enumConstants = enumConstants,
        )

        /**
         * Local storage and snapshot identity: the scope name, a colon, and [key].
         *
         * This string is not parsed back into a scope and a key. A public key that contains a
         * colon stays the remainder after the fixed prefix. Remote providers are not given this
         * string.
         */
        public fun localStorageKey(): String = "${scope.name}:$key"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ConfigParam<*>

            return key == other.key &&
                defaultValue == other.defaultValue &&
                valueType == other.valueType &&
                scope == other.scope &&
                description == other.description &&
                category == other.category &&
                since == other.since &&
                enumConstants == other.enumConstants
        }

        override fun hashCode(): Int = key.hashCode()

        override fun toString(): String =
            buildString {
                append("ConfigParam")
                append('(')

                appendIfPresent(key = "key", key, addComma = false)
                appendIfPresent(key = "defaultValue", defaultValue)
                appendIfPresent(key = "scope", scope)
                appendIfPresent(key = "description", description)
                appendIfPresent(key = "category", category)
                appendIfPresent(key = "since", since)
                appendIfPresent(key = "enumConstants", enumConstants)

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
 * The result has [FlagScope.ANY]: local, then remote, then [defaultValue]. A hand-written param
 * that omits [scope] does not change behavior. Generated code uses the overload that passes
 * [scope] explicitly.
 *
 * @param key Public name. Remote providers look this up. It is not the local storage identity.
 * @param defaultValue Value returned when no provider supplies a value for this key.
 * @param description Optional human-readable explanation shown in debug UIs.
 * @param category Optional group name used to organise related params in debug UIs.
 * @param since Optional version or date string indicating when this param was introduced.
 * @return A [ConfigParam] instance typed to [T] with [FlagScope.ANY].
 */
public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    description: String? = null,
    category: String? = null,
    since: String? = null,
    enumConstants: List<T>? = null,
): ConfigParam<T> =
    ConfigParam(
        key = key,
        defaultValue = defaultValue,
        valueType = T::class,
        description = description,
        category = category,
        since = since,
        enumConstants = enumConstants,
    )

/**
 * Creates a [ConfigParam] with an explicit [scope], without requiring a [kotlin.reflect.KClass].
 *
 * Generated local flags pass [FlagScope.LOCAL]. Generated remote flags pass [FlagScope.REMOTE].
 * [key] is stored unchanged.
 *
 * @param key Public name. Remote providers look this up. Local storage uses [ConfigParam.localStorageKey].
 * @param defaultValue Value returned when the providers this [scope] reads have no value.
 * @param scope Which providers may be read, and which local slot this param occupies.
 * @param description Optional human-readable explanation shown in debug UIs.
 * @param category Optional group name used to organise related params in debug UIs.
 * @param since Optional version or date string indicating when this param was introduced.
 * @return A [ConfigParam] instance typed to [T] with [scope].
 */
public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    scope: FlagScope,
    description: String? = null,
    category: String? = null,
    since: String? = null,
    enumConstants: List<T>? = null,
): ConfigParam<T> =
    ConfigParam(
        key = key,
        defaultValue = defaultValue,
        valueType = T::class,
        scope = scope,
        description = description,
        category = category,
        since = since,
        enumConstants = enumConstants,
    )
