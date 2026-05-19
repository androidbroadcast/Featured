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
 * Derives a PascalCase identifier from a Gradle module path.
 *
 * Splits on `:` only; segments containing hyphens or other special characters are
 * preserved as single PascalCase words (e.g. `"feature-checkout"` → `"Feature-checkout"`).
 * Used internally for identifier derivation.
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

/**
 * Derives a PascalCase file-name suffix from a Gradle module path, safe for use as a
 * Kotlin source-file name component.
 *
 * Unlike [modulePathToIdentifier], this function splits on ALL non-alphanumeric characters
 * (`:`, `-`, `.`, `_`, etc.) so that path segments like `"feature-checkout"` produce
 * `"FeatureCheckout"` rather than `"Feature-checkout"`.
 *
 * Examples:
 * - `":app"` → `"App"`
 * - `":feature:checkout"` → `"FeatureCheckout"`
 * - `":sample:feature-checkout"` → `"SampleFeatureCheckout"`
 * - `":"` → `"Root"`
 */
internal fun String.modulePathToFileSuffix(): String =
    split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { segment -> segment.replaceFirstChar { it.uppercase() } }
        .ifEmpty { "Root" }

internal fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

/**
 * Returns the name of the generated `ConfigValues` extension function for this flag.
 *
 * - Boolean flags: `is<Name>Enabled` (e.g. `isDarkModeEnabled`)
 * - All other types (including enum): `get<Name>` (e.g. `getMaxRetries`, `getCheckoutVariant`)
 */
internal fun LocalFlagEntry.extensionFunctionName(): String {
    val capitalized = propertyName.capitalized()
    return if (type == "Boolean") "is${capitalized}Enabled" else "get$capitalized"
}
