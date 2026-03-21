package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ScanLocalFlagsTask] output file and generates a
 * ProGuard/R8 `-assumevalues` rules file (`proguard-featured.pro`).
 *
 * Only Boolean flags whose `defaultValue` is `false` produce a rule, enabling
 * R8 dead-branch elimination. Flags with `defaultValue = true`, non-boolean
 * flags, and `@RemoteFlag` declarations produce no output.
 *
 * Wire the generated file into your Android module's ProGuard configuration:
 * ```kotlin
 * android {
 *     buildTypes {
 *         release {
 *             proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
 *                           "proguard-featured.pro")
 *         }
 *     }
 * }
 * ```
 */
public abstract class GenerateProguardRulesTask : DefaultTask() {
    /**
     * The line-delimited flag report produced by [ScanLocalFlagsTask].
     * Each line has the format `key|defaultValue|type|moduleName`.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val scanResultFile: RegularFileProperty

    /**
     * The generated ProGuard rules file (`proguard-featured.pro`).
     * Written to `<module>/build/featured/proguard-featured.pro`.
     */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val entries = parseScanResult()
        val rules = ProguardRulesGenerator.generate(entries)

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(rules)

        if (rules.isBlank()) {
            logger.lifecycle("[featured] No @LocalFlag(defaultValue=false) flags found — proguard-featured.pro is empty.")
        } else {
            val count = entries.count { it.type == "Boolean" && it.defaultValue == "false" }
            logger.lifecycle("[featured] Generated ProGuard rules for $count flag(s) → ${out.path}")
        }
    }

    private fun parseScanResult(): List<LocalFlagEntry> {
        val file = scanResultFile.get().asFile
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
}
