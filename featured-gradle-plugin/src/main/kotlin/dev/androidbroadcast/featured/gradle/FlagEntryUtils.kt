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
