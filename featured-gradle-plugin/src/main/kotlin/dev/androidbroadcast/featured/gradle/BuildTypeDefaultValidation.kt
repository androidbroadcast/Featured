package dev.androidbroadcast.featured.gradle

import org.gradle.api.GradleException

/**
 * Configuration-time checks for [FlagSpec.defaultFor] and [FlagSpec.discard].
 *
 * The discard check is not nested under the Android name check. A project with no build
 * types still fails a `discard(...)` whose release effective default is not `false`.
 * A bad build-type name and a true release effective default can both be reported.
 */
internal object BuildTypeDefaultValidation {
    fun failIf(errors: List<String>) {
        if (errors.isEmpty()) return
        throw GradleException(errors.joinToString(separator = "\n"))
    }

    /**
     * Local booleans that called `discard(...)` and whose release effective default is not
     * `false`. The message names the flag. A debug-only `false` does not satisfy the check.
     */
    fun discardErrors(flags: List<FlagSpec>): List<String> =
        flags.mapNotNull { flag ->
            if (flag.discards.isEmpty() || flag.type != "Boolean") return@mapNotNull null
            val release = flag.effectiveDefault(RELEASE_BUILD_TYPE)
            if (release == "false") {
                null
            } else {
                "discard(...) requires boolean flag \"${flag.key}\" to have a release effective default of false " +
                    "(got $release). A flag that is true in the release build type keeps its guarded code, so " +
                    "that code cannot be verified as discarded."
            }
        }

    /**
     * Flags that called `defaultFor` on a project with no Android application/library build
     * types. The message names the flag and says the project has no Android build types.
     */
    fun missingAndroidBuildTypesErrors(flags: List<FlagSpec>): List<String> =
        flags.mapNotNull { flag ->
            if (flag.buildTypeDefaults.isEmpty()) {
                null
            } else {
                "defaultFor(...) on flag \"${flag.key}\" cannot apply: the project has no Android build types."
            }
        }

    /**
     * Trimmed `defaultFor` names that are not build types of this project. A flavor name, a
     * variant name, and a typo fail the same way. Matching is case-sensitive.
     */
    fun unknownBuildTypeErrors(
        flags: List<FlagSpec>,
        buildTypeNames: Set<String>,
    ): List<String> =
        flags.flatMap { flag ->
            flag.buildTypeDefaults.keys
                .filter { name -> name !in buildTypeNames }
                .map { name ->
                    "defaultFor(...) on flag \"${flag.key}\" names build type \"$name\", which is not an " +
                        "Android build type of this project."
                }
        }
}
