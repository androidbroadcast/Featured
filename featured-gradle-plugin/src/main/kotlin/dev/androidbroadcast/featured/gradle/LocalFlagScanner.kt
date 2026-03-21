package dev.androidbroadcast.featured.gradle

/**
 * Scans Kotlin source text for `@LocalFlag`-annotated `ConfigParam` property declarations.
 *
 * Scanning is done at the source level (not bytecode) so it works seamlessly with
 * Kotlin Multiplatform source sets and does not require compilation to complete first.
 *
 * The scanner recognises the following pattern (with optional whitespace):
 * ```
 * @LocalFlag
 * val <name> = ConfigParam("<key>", <defaultValue>)
 * ```
 */
public object LocalFlagScanner {
    // Matches: @LocalFlag followed (possibly after blank lines) by a val/var with ConfigParam
    private val ANNOTATED_PARAM_REGEX = Regex(
        """@LocalFlag\s+(?:val|var)\s+\w+\s*=\s*ConfigParam\s*\(\s*"([^"]+)"\s*,\s*([^)]+?)\s*\)""",
        RegexOption.MULTILINE,
    )

    /**
     * Scans [source] text and returns one [LocalFlagEntry] per `@LocalFlag`-annotated
     * `ConfigParam` declaration found.
     *
     * @param source Raw Kotlin source code to scan.
     * @param moduleName Gradle module name to embed in each [LocalFlagEntry].
     */
    public fun scan(source: String, moduleName: String): List<LocalFlagEntry> =
        ANNOTATED_PARAM_REGEX.findAll(source).map { match ->
            val key = match.groupValues[1]
            val rawDefault = match.groupValues[2].trim()
            LocalFlagEntry(
                key = key,
                defaultValue = extractDefaultValue(rawDefault),
                type = inferType(rawDefault),
                moduleName = moduleName,
            )
        }.toList()

    /** Strips string quotes; leaves other literals as-is. */
    private fun extractDefaultValue(raw: String): String =
        if (raw.startsWith('"') && raw.endsWith('"')) raw.removeSurrounding("\"") else raw

    /**
     * Infers a Kotlin type name from a raw literal string.
     *
     * Rules:
     * - `"..."` → String
     * - `true` / `false` → Boolean
     * - digits only → Int
     * - digits with `.` → Double
     * - anything else → String (conservative fallback)
     */
    private fun inferType(raw: String): String =
        when {
            raw.startsWith('"') -> "String"
            raw == "true" || raw == "false" -> "Boolean"
            raw.toIntOrNull() != null -> "Int"
            raw.toDoubleOrNull() != null -> "Double"
            raw.toLongOrNull() != null -> "Long"
            raw.toFloatOrNull() != null -> "Float"
            else -> "String"
        }
}
