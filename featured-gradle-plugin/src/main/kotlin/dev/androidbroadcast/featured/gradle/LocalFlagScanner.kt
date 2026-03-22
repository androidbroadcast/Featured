package dev.androidbroadcast.featured.gradle

/**
 * Scans Kotlin source text for `@LocalFlag`-annotated `ConfigParam` property declarations.
 *
 * Scanning is done at the source level (not bytecode) so it works seamlessly with
 * Kotlin Multiplatform source sets and does not require compilation to complete first.
 *
 * Recognised patterns (positional and named arguments, with optional type argument):
 * ```kotlin
 * @LocalFlag
 * val darkMode = ConfigParam("dark_mode", false)
 *
 * @LocalFlag
 * val timeout = ConfigParam<Int>(key = "timeout", defaultValue = 30)
 * ```
 */
public object LocalFlagScanner {
    /**
     * Matches `@LocalFlag` followed by a `val`/`var` with a `ConfigParam(...)` call.
     * Captures:
     * - Group 1: the property name (identifier after `val`/`var`)
     * - Group 2: the full argument list inside `ConfigParam(...)`
     *
     * Supports:
     * - Optional generic type parameter: `ConfigParam<T>(...)`
     * - Positional args: `ConfigParam("key", value)`
     * - Named args: `ConfigParam(key = "key", defaultValue = value)`
     * - Multi-line argument lists (DOT_ALL via [RegexOption.DOT_MATCHES_ALL])
     */
    private val ANNOTATED_PARAM_REGEX =
        Regex(
            """@LocalFlag\s+(?:val|var)\s+(\w+)\s*=\s*ConfigParam(?:<[^>]+>)?\s*\(([^)]+)\)""",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL),
        )

    /**
     * Matches the simple name of an enclosing `object` or `companion object` declaration.
     * Used to find the nearest enclosing owner of a `@LocalFlag` property.
     *
     * Group 1: object name (e.g. `"NewCheckoutFlags"` from `object NewCheckoutFlags {`).
     * `companion object` is matched but yields an empty group 1.
     */
    private val OBJECT_DECL_REGEX =
        Regex(
            """(?:companion\s+object|object\s+(\w+))\s*(?::[^{]*)?\{""",
            setOf(RegexOption.MULTILINE),
        )

    // Matches the key in positional form: first argument is a string literal
    private val POSITIONAL_KEY_REGEX = Regex("""^\s*"([^"]+)"\s*,""")

    // Matches the key in named form: key = "..."
    private val NAMED_KEY_REGEX = Regex("""key\s*=\s*"([^"]+)"""")

    // Matches the defaultValue in named form: defaultValue = <expr>
    private val NAMED_DEFAULT_REGEX = Regex("""defaultValue\s*=\s*([^,)\n]+)""")

    /**
     * Scans [source] text and returns one [LocalFlagEntry] per `@LocalFlag`-annotated
     * `ConfigParam` declaration found.
     *
     * In addition to key, defaultValue, and type the scanner also captures:
     * - [LocalFlagEntry.propertyName] — the Kotlin property identifier.
     * - [LocalFlagEntry.ownerName] — the simple name of the nearest enclosing
     *   `object` or `companion object`, or `null` for top-level declarations.
     *
     * @param source Raw Kotlin source code to scan.
     * @param moduleName Gradle module name to embed in each [LocalFlagEntry].
     */
    public fun scan(
        source: String,
        moduleName: String,
    ): List<LocalFlagEntry> {
        val ownerIndex = buildOwnerIndex(source)
        return ANNOTATED_PARAM_REGEX
            .findAll(source)
            .mapNotNull { match ->
                val propertyName = match.groupValues[1]
                val args = match.groupValues[2]
                val key = parseKey(args) ?: return@mapNotNull null
                val rawDefault = parseDefaultValue(args)?.trim() ?: return@mapNotNull null
                LocalFlagEntry(
                    key = key,
                    defaultValue = extractDefaultValue(rawDefault),
                    type = inferType(rawDefault),
                    moduleName = moduleName,
                    propertyName = propertyName,
                    ownerName = ownerIndex.resolveOwner(match.range.first),
                )
            }.toList()
    }

    /**
     * Builds a sorted list of (startOffset, ownerName?) pairs representing each
     * `object` or `companion object` block found in [source].
     * Used by [resolveOwner] to determine the enclosing owner of a property.
     */
    private fun buildOwnerIndex(source: String): List<Pair<Int, String?>> =
        OBJECT_DECL_REGEX
            .findAll(source)
            .map { m ->
                val name = m.groupValues[1].takeIf { it.isNotEmpty() }
                m.range.first to name
            }.sortedBy { it.first }
            .toList()

    /**
     * Returns the name of the nearest `object` or `companion object` whose opening `{`
     * appears before [offset], or `null` if no such enclosing object is found.
     *
     * This is a heuristic: it picks the last object declaration whose start offset is
     * less than [offset], which covers the common pattern where `@LocalFlag` properties
     * are declared directly inside a single-level `object` block.
     */
    private fun List<Pair<Int, String?>>.resolveOwner(offset: Int): String? = lastOrNull { (start, _) -> start < offset }?.second

    private fun parseKey(args: String): String? =
        NAMED_KEY_REGEX.find(args)?.groupValues?.get(1)
            ?: POSITIONAL_KEY_REGEX.find(args.trimStart())?.groupValues?.get(1)

    private fun parseDefaultValue(args: String): String? {
        // Named form: defaultValue = <expr>
        NAMED_DEFAULT_REGEX
            .find(args)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.let { return it }
        // Positional form: second argument after the key string
        val afterFirstComma =
            args.indexOf(',').takeIf { it >= 0 }?.let { idx ->
                args.substring(idx + 1).trimStart()
            } ?: return null
        // Skip if the second item looks like a named argument for something else
        if (afterFirstComma.contains('=') && !afterFirstComma.startsWith("defaultValue")) return null
        return afterFirstComma.trimEnd().trimEnd(',')
    }

    /** Strips string quotes and trailing Kotlin literal suffixes (e.g. `L`, `f`, `F`). */
    private fun extractDefaultValue(raw: String): String =
        when {
            raw.startsWith('"') && raw.endsWith('"') -> raw.removeSurrounding("\"")
            else -> normalizeLiteral(raw)
        }

    /**
     * Infers a Kotlin type name from a raw literal string.
     *
     * Rules (in order of checks):
     * - `"..."` → String
     * - `true` / `false` → Boolean
     * - ends with `L` and numeric body → Long
     * - ends with `f` or `F` and numeric body → Float
     * - parses as Int → Int
     * - parses as Long → Long
     * - parses as Double → Double
     * - anything else → String (conservative fallback)
     */
    private fun inferType(raw: String): String {
        if (raw.startsWith('"')) return "String"
        if (raw == "true" || raw == "false") return "Boolean"
        // Kotlin suffix literals (e.g. 123L, 3.14f)
        if (raw.endsWith('L') && raw.dropLast(1).normNumeric().toLongOrNull() != null) return "Long"
        if ((raw.endsWith('f') || raw.endsWith('F')) &&
            raw.dropLast(1).normNumeric().toFloatOrNull() != null
        ) {
            return "Float"
        }
        val norm = raw.normNumeric()
        if (norm.toIntOrNull() != null) return "Int"
        if (norm.toLongOrNull() != null) return "Long"
        if (norm.toDoubleOrNull() != null) return "Double"
        return "String"
    }

    /** Removes underscores and leading sign for numeric parsing; strips suffix for display. */
    private fun normalizeLiteral(raw: String): String = raw.trimEnd('L', 'l', 'f', 'F').replace("_", "")

    /** Strips underscores from a numeric string for stdlib parsing. */
    private fun String.normNumeric(): String = replace("_", "")
}
