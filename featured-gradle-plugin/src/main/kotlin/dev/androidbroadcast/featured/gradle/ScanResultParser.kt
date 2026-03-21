package dev.androidbroadcast.featured.gradle

import org.gradle.api.file.RegularFileProperty

/**
 * Parses the line-delimited scan result file produced by [ScanLocalFlagsTask] into
 * a list of [LocalFlagEntry] records.
 *
 * Each non-blank line must have the format `key|defaultValue|type|moduleName`.
 * Lines that do not conform are silently skipped.
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
