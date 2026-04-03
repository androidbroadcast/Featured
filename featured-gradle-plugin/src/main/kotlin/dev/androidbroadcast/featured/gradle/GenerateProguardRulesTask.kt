package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ResolveFlagsTask] output and generates per-function
 * ProGuard/R8 `-assumevalues` rules for all local flags in this module.
 *
 * Each local flag gets its own rule targeting the generated extension function from
 * [ExtensionFunctionGenerator], so R8 can propagate the exact constant and eliminate
 * dead branches in release builds.
 *
 * Wire the generated file into your Android module's ProGuard configuration:
 * ```kotlin
 * android {
 *     buildTypes {
 *         release {
 *             proguardFiles(
 *                 getDefaultProguardFile("proguard-android-optimize.txt"),
 *                 layout.buildDirectory.file("featured/proguard-featured.pro").get().asFile,
 *             )
 *         }
 *     }
 * }
 * ```
 */
@CacheableTask
public abstract class GenerateProguardRulesTask : DefaultTask() {
    /** The flag report produced by [ResolveFlagsTask]. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val scanResultFile: RegularFileProperty

    /**
     * The Gradle module path (e.g. `":feature:ui"`), used to derive the JVM class name
     * of the generated extensions file that the ProGuard rules target.
     */
    @get:Input
    public abstract val modulePath: Property<String>

    /** Generated ProGuard rules file. Written to `build/featured/proguard-featured.pro`. */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun generate() {
        val entries = scanResultFile.parseLocalFlagEntries()
        val rules = ProguardRulesGenerator.generate(entries, modulePath.get())

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(rules)

        val count = entries.count { it.isLocal }
        if (rules.isBlank()) {
            logger.lifecycle("[featured] No local flags — proguard-featured.pro is empty.")
        } else {
            logger.lifecycle("[featured] Generated ProGuard rules for $count local flag(s) → ${out.path}")
        }
    }
}
