package dev.androidbroadcast.featured.gradle

import org.gradle.api.file.RegularFileProperty

/**
 * Reads the line-delimited flag report produced by [ScanLocalFlagsTask] and returns
 * the parsed entries.
 *
 * Supported line formats (for backwards compatibility):
 * - 4-field: `key|defaultValue|type|moduleName`
 * - 6-field: `key|defaultValue|type|moduleName|propertyName|ownerName`
 *   where `ownerName` may be empty (top-level declaration).
 *
 * Returns an empty list when the file does not exist or is empty.
 * Ignores lines that do not conform to either expected format.
 */
internal fun RegularFileProperty.parseLocalFlagEntries(): List<LocalFlagEntry> {
    val file = get().asFile
    if (!file.exists() || file.readText().isBlank()) return emptyList()
    return file
        .readLines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|")
            when (parts.size) {
                4 -> {
                    LocalFlagEntry(
                        key = parts[0],
                        defaultValue = parts[1],
                        type = parts[2],
                        moduleName = parts[3],
                    )
                }

                6 -> {
                    LocalFlagEntry(
                        key = parts[0],
                        defaultValue = parts[1],
                        type = parts[2],
                        moduleName = parts[3],
                        propertyName = parts[4],
                        ownerName = parts[5].takeIf { it.isNotEmpty() },
                    )
                }

                else -> {
                    null
                }
            }
        }
}
