package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ScanLocalFlagsTask] output and generates two Kotlin
 * source files supporting Kotlin/Native dead-branch elimination on iOS:
 *
 * - **`commonMain`** ([commonMainOutputFile]) — `expect val` declarations, one per
 *   `@LocalFlag`-annotated `ConfigParam`.
 * - **`iosMain`** ([iosMainOutputFile]) — `actual const val` declarations whose
 *   values are the flags' compile-time `defaultValue`s. LLVM inlines these constants
 *   and eliminates unreachable branches in release iOS framework builds.
 *
 * Both files are placed under `<module>/build/generated/featured/` and must be
 * registered as generated source directories in the corresponding KMP source sets.
 */
public abstract class GenerateIosConstValTask : DefaultTask() {
    /**
     * The line-delimited flag report produced by [ScanLocalFlagsTask].
     * Each line has the format `key|defaultValue|type|moduleName`.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val scanResultFile: RegularFileProperty

    /**
     * Generated Kotlin file for `iosMain` containing `actual const val` declarations.
     * Written to `<module>/build/generated/featured/iosMain/FeatureFlagOverrides.kt`.
     */
    @get:OutputFile
    public abstract val iosMainOutputFile: RegularFileProperty

    /**
     * Generated Kotlin file for `commonMain` containing `expect val` declarations.
     * Written to `<module>/build/generated/featured/commonMain/FeatureFlagExpect.kt`.
     */
    @get:OutputFile
    public abstract val commonMainOutputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val entries = scanResultFile.parseLocalFlagEntries()

        val iosContent = IosConstValGenerator.generate(entries)
        val iosOut = iosMainOutputFile.get().asFile
        iosOut.parentFile?.mkdirs()
        iosOut.writeText(iosContent)

        val commonContent = IosConstValGenerator.generateExpect(entries)
        val commonOut = commonMainOutputFile.get().asFile
        commonOut.parentFile?.mkdirs()
        commonOut.writeText(commonContent)

        if (entries.isEmpty()) {
            logger.lifecycle("[featured] No @LocalFlag declarations found — iOS const val files are empty.")
        } else {
            logger.lifecycle("[featured] Generated iOS const val declarations for ${entries.size} flag(s) → ${iosOut.path}")
            logger.lifecycle("[featured] Generated commonMain expect declarations → ${commonOut.path}")
        }
    }
}
