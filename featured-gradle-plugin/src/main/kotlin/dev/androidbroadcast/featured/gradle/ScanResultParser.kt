package dev.androidbroadcast.featured.gradle

import org.gradle.api.file.RegularFileProperty

/**
 * Reads the flag report produced by [ResolveFlagsTask] and returns the parsed entries.
 *
 * Supported formats (pipe-delimited, for backward compatibility during migration):
 * - 4-field (legacy): `key|defaultValue|type|moduleName`
 * - 6-field (legacy): `key|defaultValue|type|moduleName|propertyName|ownerName`
 * - 7-field: `key|defaultValue|type|moduleName|propertyName|ownerName|flagType`
 * - 10-field (current): `key|defaultValue|type|moduleName|propertyName|flagType|description|category|expiresAt`
 *
 * Returns an empty list when the file does not exist or is empty.
 * Ignores lines that do not conform to any expected format.
 */
internal fun RegularFileProperty.parseLocalFlagEntries(): List<LocalFlagEntry> {
    val file = get().asFile
    if (!file.exists() || file.readText().isBlank()) return emptyList()
    return file
        .readLines()
        .filter { it.isNotBlank() }
        .mapNotNull { line -> parseLine(line) }
}

private fun parseLine(line: String): LocalFlagEntry? {
    val parts = line.split("|")
    return when (parts.size) {
        4 -> LocalFlagEntry(
            key = parts[0],
            defaultValue = parts[1],
            type = parts[2],
            moduleName = parts[3],
            propertyName = parts[0].toCamelCase(),
        )
        6 -> LocalFlagEntry(
            key = parts[0],
            defaultValue = parts[1],
            type = parts[2],
            moduleName = parts[3],
            propertyName = parts[4].ifEmpty { parts[0].toCamelCase() },
        )
        7 -> LocalFlagEntry(
            key = parts[0],
            defaultValue = parts[1],
            type = parts[2],
            moduleName = parts[3],
            propertyName = parts[4].ifEmpty { parts[0].toCamelCase() },
            flagType = parts[6].ifEmpty { LocalFlagEntry.FLAG_TYPE_LOCAL },
        )
        9 -> LocalFlagEntry(
            key = parts[0],
            defaultValue = parts[1],
            type = parts[2],
            moduleName = parts[3],
            propertyName = parts[4].ifEmpty { parts[0].toCamelCase() },
            flagType = parts[5].ifEmpty { LocalFlagEntry.FLAG_TYPE_LOCAL },
            description = parts[6].ifEmpty { null },
            category = parts[7].ifEmpty { null },
            expiresAt = parts[8].ifEmpty { null },
        )
        else -> null
    }
}
