package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam

/**
 * Parses a raw text [input] into a typed value matching the type declared by [param].
 *
 * Returns `null` when:
 * - The text cannot be parsed into the expected type (validation failure).
 * - The param type is [Boolean] — booleans are controlled by a toggle, not a text field.
 * - The param type is not a supported scalar ([String], [Int], [Long], [Float], [Double]).
 *
 * Dispatches on [ConfigParam.valueType] (the declared [kotlin.reflect.KClass]) rather than
 * on the runtime type of [ConfigParam.defaultValue], which is more robust and consistent
 * with the library's type-safe design.
 */
internal fun <T : Any> parseInput(
    param: ConfigParam<T>,
    input: String,
): T? {
    @Suppress("UNCHECKED_CAST")
    return when (param.valueType) {
        String::class -> input as T
        Int::class -> input.toIntOrNull() as T?
        Long::class -> input.toLongOrNull() as T?
        Float::class -> input.toFloatOrNull() as T?
        Double::class -> input.toDoubleOrNull() as T?
        else -> null
    }
}

/**
 * Returns `true` when [param] is a scalar type supported by [parseInput]:
 * [String], [Int], [Long], [Float], or [Double].
 */
internal fun isScalarParam(param: ConfigParam<*>): Boolean =
    param.valueType in setOf(String::class, Int::class, Long::class, Float::class, Double::class)
