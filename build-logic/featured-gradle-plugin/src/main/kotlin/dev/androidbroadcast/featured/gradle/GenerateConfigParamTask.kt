package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ResolveFlagsTask] output and generates three Kotlin source files:
 *
 * - `GeneratedLocalFlags.kt` — internal object with one `ConfigParam` per local flag.
 * - `GeneratedRemoteFlags.kt` — internal object with one `ConfigParam` per remote flag.
 * - `GeneratedFlagExtensions<Suffix>.kt` — internal extension functions on `ConfigValues`,
 *   one per flag. The suffix is derived from [modulePath] (e.g. `SampleFeatureCheckout`)
 *   so that each module's file produces a unique JVM class name.
 *
 * All files are written to [outputDir] (`build/generated/featured/commonMain/`).
 * Add [outputDir] to the Kotlin compilation source set:
 * ```kotlin
 * kotlin {
 *     sourceSets.commonMain.get().kotlin.srcDir(
 *         tasks.named("generateConfigParam").map { it.outputDir }
 *     )
 * }
 * ```
 */
@CacheableTask
public abstract class GenerateConfigParamTask : DefaultTask() {
    /** The flag report produced by [ResolveFlagsTask]. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val flagsFile: RegularFileProperty

    /** The Gradle module path (e.g. `":sample:feature-checkout"`) used to derive the file-name suffix. */
    @get:Input
    public abstract val modulePath: Property<String>

    /** Output directory receiving the three generated `.kt` files. */
    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun generate() {
        val entries = flagsFile.parseLocalFlagEntries()
        val dir = outputDir.get().asFile
        // Clean before writing — the extension file name changed from the fixed
        // "GeneratedFlagExtensions.kt" to a module-specific name, so stale files
        // from previous runs must be removed to avoid duplicate-class compile errors.
        dir.deleteRecursively()
        dir.mkdirs()

        val (localSource, remoteSource) = ConfigParamGenerator.generate(entries)
        val extensionsSource = ExtensionFunctionGenerator.generate(entries, modulePath.get())

        if (localSource.isNotEmpty()) {
            dir.resolve("GeneratedLocalFlags.kt").writeText(localSource)
        }
        if (remoteSource.isNotEmpty()) {
            dir.resolve("GeneratedRemoteFlags.kt").writeText(remoteSource)
        }
        if (extensionsSource.isNotEmpty()) {
            dir.resolve(ExtensionFunctionGenerator.fileName(modulePath.get())).writeText(extensionsSource)
        }

        val local = entries.count { it.isLocal }
        val remote = entries.count { !it.isLocal }
        logger.lifecycle(
            "[featured] Generated ConfigParam + extensions: $local local, $remote remote flag(s) → ${dir.path}",
        )
    }
}
