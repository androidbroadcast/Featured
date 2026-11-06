package dev.androidbroadcast.featured

import kotlin.reflect.KClass

/*
 * Minimal stand-in for the `:core` runtime, scoped to this TestKit fixture only.
 * Generated sources import ConfigParam / ConfigValues / ConfigValue from `:core`, which a
 * TestKit project cannot resolve. Keep this surface in lockstep with the generator.
 */

enum class FlagScope { LOCAL, REMOTE, ANY }

class ConfigParam<T : Any>
    constructor(
        val key: String,
        val defaultValue: T,
        val valueType: KClass<T>,
        val scope: FlagScope = FlagScope.ANY,
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

inline fun <reified T : Any> ConfigParam(
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

class ConfigValue<T : Any>(
    val value: T,
)

class ConfigValues {
    /**
     * Must not return a value R8 can see as a constant. The generated accessor is
     * `getValueCached(param).value`. Returning `param.defaultValue` here lets R8 fold the
     * flag without the plugin's `-assumevalues` rule, so a passing `discard()` proves nothing.
     * Fault-injection tests replace this delegation with `ConfigValue(param.defaultValue)`.
     * The native call is opaque, so the boolean is unknown unless the rule pins it.
     */
    fun <T : Any> getValueCached(param: ConfigParam<T>): ConfigValue<T> = opaqueValue(param)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> opaqueValue(param: ConfigParam<T>): ConfigValue<T> {
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
