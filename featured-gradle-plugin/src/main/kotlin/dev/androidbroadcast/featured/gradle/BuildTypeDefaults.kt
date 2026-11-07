package dev.androidbroadcast.featured.gradle

import java.util.Locale

/** Android build type whose effective default is what release R8 rules assume. */
internal const val RELEASE_BUILD_TYPE: String = "release"

/**
 * Build-type default selection. Pure: no Gradle project, no variant, no flavor.
 *
 * The effective default of build type [buildType] is the override declared for that name,
 * otherwise the base default. An unspecified name, including a custom build type such as
 * `benchmark`, uses the base. Flavor names are not inputs.
 */
internal object BuildTypeDefaults {
    fun effective(
        base: String,
        overrides: Map<String, String>,
        buildType: String,
    ): String = overrides[buildType] ?: base

    fun releaseEffective(
        base: String,
        overrides: Map<String, String>,
    ): String = effective(base, overrides, RELEASE_BUILD_TYPE)

    /**
     * One override line: `key|buildType|serializedValue`. [serializedValue] is the remainder
     * after the second pipe, so a value that itself contains `|` round-trips.
     */
    fun descriptor(
        key: String,
        buildType: String,
        serializedValue: String,
    ): String = "$key|$buildType|$serializedValue"

    /**
     * Parses override lines into `flagKey -> (buildType -> serializedValue)`.
     * A repeated key is last-write-wins, matching generated-object emission.
     */
    fun parse(lines: List<String>): Map<String, Map<String, String>> {
        val parsed = linkedMapOf<String, LinkedHashMap<String, String>>()
        for (line in lines) {
            val first = line.indexOf('|')
            val second = if (first < 0) -1 else line.indexOf('|', startIndex = first + 1)
            if (first <= 0 || second <= first + 1) continue
            val key = line.substring(0, first)
            val buildType = line.substring(first + 1, second)
            val value = line.substring(second + 1)
            parsed.getOrPut(key) { linkedMapOf() }[buildType] = value
        }
        return parsed
    }

    /** `generateConfigParam` plus the capitalization AGP uses in `compile${BuildType}Kotlin`. */
    fun taskName(buildType: String): String = GENERATE_CONFIG_PARAM_TASK_NAME + buildType.agpCapitalized()
}

/**
 * First-character capitalization matching AGP `String.appendCapitalized` (`Locale.US`).
 * `debug` becomes `Debug`, so the task name is `generateConfigParamDebug`.
 */
internal fun String.agpCapitalized(): String {
    if (isEmpty()) return this
    val first = codePointAt(0)
    val count = Character.charCount(first)
    return substring(0, count).uppercase(Locale.US) + substring(count)
}

/**
 * Replaces each local entry's [LocalFlagEntry.defaultValue] with the effective default of
 * [buildType]. Remote entries keep their single default. An empty override list means every
 * build type uses the base.
 */
internal fun List<LocalFlagEntry>.withEffectiveDefaults(
    buildType: String,
    overrideLines: List<String>,
): List<LocalFlagEntry> {
    val overrides = BuildTypeDefaults.parse(overrideLines)
    return map { entry ->
        if (!entry.isLocal) {
            entry
        } else {
            entry.copy(
                defaultValue =
                    BuildTypeDefaults.effective(
                        entry.defaultValue,
                        overrides[entry.key].orEmpty(),
                        buildType,
                    ),
            )
        }
    }
}

internal fun List<LocalFlagEntry>.withReleaseEffectiveDefaults(overrideLines: List<String>): List<LocalFlagEntry> =
    withEffectiveDefaults(RELEASE_BUILD_TYPE, overrideLines)
