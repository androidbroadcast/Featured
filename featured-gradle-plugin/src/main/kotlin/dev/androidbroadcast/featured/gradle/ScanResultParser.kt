package dev.androidbroadcast.featured.gradle

import org.gradle.api.file.RegularFileProperty

/**
 * Reads the line-delimited flag report produced by [ScanLocalFlagsTask] and returns
 * the parsed entries. Each line must have the format `key|defaultValue|type|moduleName`.
 *
 * Returns an empty list when the file does not exist, is empty, or contains
 * lines that do not conform to the expected format.
 */
internal fun RegularFileProperty.parseLocalFlagEntries(): List<LocalFlagEntry> {
    val file = get().asFile
    if (!file.exists() || file.readText().isBlank()) return emptyList()
    return file
        .readLines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 4) return@mapNotNull null
            LocalFlagEntry(
                key = parts[0],
                defaultValue = parts[1],
                type = parts[2],
                moduleName = parts[3],
            )
        }
}
