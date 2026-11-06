package dev.androidbroadcast.featured

import kotlin.reflect.KClass

/*
 * Minimal stand-in for the `:core` runtime, scoped to this TestKit fixture only.
 * Generated sources import ConfigParam / ConfigValues / ConfigValue from `:core`, which a
 * TestKit project cannot resolve. Keep this surface in lockstep with the generator.
 */

class ConfigParam<T : Any>
    constructor(
        val key: String,
        val defaultValue: T,
        val valueType: KClass<T>,
        val description: String? = null,
        val category: String? = null,
        val since: String? = null,
        val enumConstants: List<T>? = null,
    )

inline fun <reified T : Any> ConfigParam(
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

class ConfigValue<T : Any>(
    val value: T,
)

class ConfigValues {
    /**
     * Must not return a value R8 can see as a constant. The generated accessor is
     * `getValueCached(param).value`; if this returns `param.defaultValue`, R8 folds the
     * flag without the plugin's `-assumevalues` rule and a passing `discard()` proves nothing.
     * A native call is opaque to R8, so the boolean is unknown unless the rule pins it.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getValueCached(param: ConfigParam<T>): ConfigValue<T> {
        val value =
            if (param.defaultValue is Boolean && readUnknownBoolean()) {
                true as T
            } else {
                param.defaultValue
            }
        return ConfigValue(value)
    }

    private external fun readUnknownBoolean(): Boolean
}
