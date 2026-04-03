package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
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
@CacheableTask
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
        val entries = scanResultFile.parseLocalFlagEntries()
        val content = XcconfigGenerator.generate(entries)

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(content)

        if (content.isBlank()) {
            logger.lifecycle("[featured] No local boolean flags with defaultValue=false — FeatureFlags.generated.xcconfig is empty.")
        } else {
            val count = entries.count { it.type == "Boolean" && it.defaultValue == "false" }
            logger.lifecycle("[featured] Generated xcconfig with $count DISABLE_ condition(s) → ${out.path}")
        }
    }
}
