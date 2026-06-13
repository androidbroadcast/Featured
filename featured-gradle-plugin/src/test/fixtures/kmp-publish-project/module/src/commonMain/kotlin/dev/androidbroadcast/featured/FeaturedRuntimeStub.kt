package dev.androidbroadcast.featured

import kotlin.reflect.KClass

/*
 * Minimal stand-in for the `:core` Featured runtime, scoped to this TestKit fixture only.
 *
 * The Featured plugin auto-wires its generated `ConfigParam` objects and `ConfigValues`
 * extensions into this module's `commonMain` compilation. Those generated sources `import
 * dev.androidbroadcast.featured.ConfigParam` / `ConfigValues` / `ConfigValue` from `:core`, which is
 * a sibling Gradle project not resolvable from a TestKit fixture. This stub provides exactly the
 * symbols the generator emits so the generated sources compile during publication, exercising
 * generated-code compilation as part of the integration test. The API surface is derived from
 * ConfigParamGenerator / ExtensionFunctionGenerator and kept in lockstep with the real `:core` types.
 */

class ConfigParam<T : Any>
    // public (not internal) so generated `ConfigParam(...)` factory calls in this
    // TestKit fixture compile — mirrors the android-project / android-library stub copies.
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
    fun <T : Any> getValueCached(param: ConfigParam<T>): ConfigValue<T> = ConfigValue(param.defaultValue)
}
