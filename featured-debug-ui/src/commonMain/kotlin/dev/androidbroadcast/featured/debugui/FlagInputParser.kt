package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam

/**
 * Parses a raw text [input] into a typed value matching the type declared by [param].
 *
 * Returns `null` when:
 * - The text cannot be parsed into the expected type (validation failure).
 * - The param type is [Boolean] — booleans are controlled by a toggle, not a text field.
 *
 * Supported scalar types: [String], [Int], [Long], [Float], [Double].
 */
internal fun <T : Any> parseInput(param: ConfigParam<T>, input: String): T? {
    @Suppress("UNCHECKED_CAST")
    return when (param.defaultValue) {
        is String -> input as T
        is Int -> input.toIntOrNull() as T?
        is Long -> input.toLongOrNull() as T?
        is Float -> input.toFloatOrNull() as T?
        is Double -> input.toDoubleOrNull() as T?
        else -> null
    }
}
