package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ScanLocalFlagsTask] output file and generates an Xcode
 * `.xcconfig` file (`FeatureFlags.generated.xcconfig`) with `SWIFT_ACTIVE_COMPILATION_CONDITIONS`
 * entries for all `@LocalFlag`-annotated flags with `defaultValue = false`.
 *
 * This enables Swift compiler dead code elimination (DCE) at iOS entry points.
 * Include the generated file in your Xcode Release configuration:
 * ```
 * // In Xcode: Project → Info → Configurations → Release → select FeatureFlags.generated.xcconfig
 * ```
 *
 * The output file is written to `<module>/build/featured/FeatureFlags.generated.xcconfig`.
 */
public abstract class GenerateXcconfigTask : DefaultTask() {

    /**
     * The line-delimited flag report produced by [ScanLocalFlagsTask].
     * Each line has the format `key|defaultValue|type|moduleName`.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val scanResultFile: RegularFileProperty

    /**
     * The generated xcconfig file (`FeatureFlags.generated.xcconfig`).
     * Written to `<module>/build/featured/FeatureFlags.generated.xcconfig`.
     */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val entries = parseScanResult()
        val content = XcconfigGenerator.generate(entries)

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(content)

        if (content.isBlank()) {
            logger.lifecycle("[featured] No @LocalFlag(defaultValue=false) flags found — FeatureFlags.generated.xcconfig is empty.")
        } else {
            val count = entries.count { it.type == "Boolean" && it.defaultValue == "false" }
            logger.lifecycle("[featured] Generated xcconfig with $count DISABLE_ condition(s) → ${out.path}")
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
