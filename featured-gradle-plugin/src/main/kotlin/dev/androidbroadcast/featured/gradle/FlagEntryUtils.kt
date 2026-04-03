package dev.androidbroadcast.featured.gradle

/**
 * Converts a snake_case key string to a camelCase Kotlin property name.
 *
 * Examples:
 * - `"dark_mode"` → `"darkMode"`
 * - `"new_checkout_flow"` → `"newCheckoutFlow"`
 * - `"enabled"` → `"enabled"`
 */
internal fun String.toCamelCase(): String =
    split("_")
        .mapIndexed { index, word ->
            if (index == 0) word.lowercase() else word.replaceFirstChar { it.uppercase() }
        }.joinToString("")

/**
 * Derives a unique JVM class name suffix from a Gradle module path, used in
 * `@file:JvmName(...)` to prevent class name conflicts across modules.
 *
 * Examples:
 * - `":app"` → `"App"`
 * - `":feature:checkout"` → `"FeatureCheckout"`
 * - `":"` → `"Root"`
 */
internal fun String.modulePathToIdentifier(): String =
    removePrefix(":")
        .split(":")
        .filter { it.isNotBlank() }
        .joinToString("") { segment -> segment.replaceFirstChar { it.uppercase() } }
        .ifEmpty { "Root" }

internal fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

/**
 * Returns the name of the generated `ConfigValues` extension function for this flag.
 *
 * - Boolean flags: `is<Name>Enabled` (e.g. `isDarkModeEnabled`)
 * - All other types: `get<Name>` (e.g. `getMaxRetries`)
 */
internal fun LocalFlagEntry.extensionFunctionName(): String {
    val capitalized = propertyName.capitalized()
    return if (type == "Boolean") "is${capitalized}Enabled" else "get$capitalized"
}
