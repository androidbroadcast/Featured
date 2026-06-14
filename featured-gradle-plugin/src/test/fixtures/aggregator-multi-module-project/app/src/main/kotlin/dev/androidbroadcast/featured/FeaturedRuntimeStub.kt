package dev.androidbroadcast.featured

import kotlin.reflect.KClass

/*
 * Minimal stand-in for the `:core` Featured runtime, scoped to this TestKit fixture only.
 *
 * The aggregator plugin auto-wires its generated `GeneratedFeaturedRegistry` into this app's
 * compilation. That generated source `import`s `dev.androidbroadcast.featured.ConfigParam` from
 * `:core`, which is a sibling Gradle project not resolvable from a TestKit fixture. This stub
 * provides exactly the symbols the registry generator emits so the generated source compiles,
 * exercising generated-code compilation as proof the wiring happened. The API surface is derived
 * from GeneratedFeaturedRegistryGenerator and kept in lockstep with the real `:core` types.
 */

class ConfigParam<T : Any>
    // Public (not internal) so the inline reified factory below can call it — a public inline
    // function cannot reference a non-public-API constructor. This is a test-fixture stub, not
    // real :core API, so the wider visibility is harmless.
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
