package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ScanLocalFlagsTask] output file and generates a
 * `GeneratedFlagRegistrar.kt` source file containing an `object GeneratedFlagRegistrar`
 * with a single `register()` function that calls `FlagRegistry.register(...)` for every
 * `@LocalFlag`-annotated `ConfigParam` in this module.
 *
 * The generated file is KMP-safe — it uses only APIs available in `commonMain`.
 *
 * Wire the generated source directory into the Kotlin compilation:
 * ```kotlin
 * kotlin {
 *     sourceSets.commonMain.get().kotlin.srcDir(
 *         tasks.named("generateFlagRegistrar").map { it.outputFile.get().asFile.parentFile }
 *     )
 * }
 * ```
 *
 * The [FeaturedPlugin] performs this wiring automatically for all registered Kotlin source sets.
 */
public abstract class GenerateFlagRegistrarTask : DefaultTask() {
    /**
     * The line-delimited flag report produced by [ScanLocalFlagsTask].
     * Each line has the format `key|defaultValue|type|moduleName|propertyName|ownerName`.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val scanResultFile: RegularFileProperty

    /**
     * Kotlin package name used in the generated `GeneratedFlagRegistrar` object.
     * Defaults to `"dev.androidbroadcast.featured.generated"`.
     */
    @get:Input
    public abstract val packageName: Property<String>

    /**
     * The generated `GeneratedFlagRegistrar.kt` file.
     * Written to `<module>/build/generated/featured/GeneratedFlagRegistrar.kt`.
     */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val entries = scanResultFile.parseLocalFlagEntries()
        val source = FlagRegistrarGenerator.generate(entries, packageName.get())

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(source)

        if (entries.isEmpty()) {
            logger.lifecycle("[featured] No @LocalFlag declarations found — GeneratedFlagRegistrar.register() is empty.")
        } else {
            logger.lifecycle("[featured] Generated FlagRegistrar with ${entries.size} registration(s) → ${out.path}")
        }
    }
}
