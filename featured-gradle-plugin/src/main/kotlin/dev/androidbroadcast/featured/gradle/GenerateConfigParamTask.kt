package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the [ResolveFlagsTask] output and generates four Kotlin source files:
 *
 * - `GeneratedLocalFlags<Suffix>.kt` — object with one `ConfigParam` per local flag.
 * - `GeneratedRemoteFlags<Suffix>.kt` — object with one `ConfigParam` per remote flag.
 * - `GeneratedLocalFlagExtensions<Suffix>.kt` / `GeneratedRemoteFlagExtensions<Suffix>.kt` —
 *   extension functions on `ConfigValues`, one per flag. The suffix is derived from
 *   [modulePath] (e.g. `SampleFeatureCheckout`) so that each module's file produces a
 *   unique JVM class name.
 *
 * Package, object names, and visibility follow the effective `generation { }` DSL settings
 * carried by the `local*` / `remote*` properties; [FeaturedPlugin] resolves the section /
 * module-wide fallback chain before wiring them.
 *
 * All files are written to [outputDir] (`build/generated/featured/commonMain/`). [FeaturedPlugin]
 * auto-wires this directory into the consumer module's compilation (KMP `commonMain`, Kotlin/JVM
 * `main`, or the AGP `main` Kotlin source set) — no manual `srcDir` / `dependsOn` is needed.
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

    /** Effective package for the local flags object and its extensions file. */
    @get:Input
    public abstract val localPackageName: Property<String>

    /** Custom local flags object name; absent → `GeneratedLocalFlags<Suffix>`. */
    @get:Input
    @get:Optional
    public abstract val localClassName: Property<String>

    /** Effective visibility for the local flags object and its extensions. */
    @get:Input
    public abstract val localVisibility: Property<FeaturedVisibility>

    /** Effective package for the remote flags object and its extensions file. */
    @get:Input
    public abstract val remotePackageName: Property<String>

    /** Custom remote flags object name; absent → `GeneratedRemoteFlags<Suffix>`. */
    @get:Input
    @get:Optional
    public abstract val remoteClassName: Property<String>

    /** Effective visibility for the remote flags object and its extensions. */
    @get:Input
    public abstract val remoteVisibility: Property<FeaturedVisibility>

    /** Output directory receiving the generated `.kt` files. */
    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    init {
        // Conventions cover direct task usage; FeaturedPlugin always wires the resolved
        // DSL values explicitly.
        localPackageName.convention(ConfigParamGenerator.DEFAULT_PACKAGE)
        remotePackageName.convention(ConfigParamGenerator.DEFAULT_PACKAGE)
        localVisibility.convention(FeaturedVisibility.INTERNAL)
        remoteVisibility.convention(FeaturedVisibility.INTERNAL)
    }

    @TaskAction
    public fun generate() {
        val entries = flagsFile.parseLocalFlagEntries()
        val dir = outputDir.get().asFile
        // Clean before writing — custom class names change the output file names between
        // runs, so stale files from previous runs must be removed to avoid duplicate-class
        // compile errors.
        dir.deleteRecursively()
        dir.mkdirs()

        val path = modulePath.get()
        val localCodegen = ObjectCodegen(localPackageName.get(), localClassName.orNull, localVisibility.get())
        val remoteCodegen = ObjectCodegen(remotePackageName.get(), remoteClassName.orNull, remoteVisibility.get())
        validateDistinctObjectFqns(path, localCodegen, remoteCodegen)

        val (localSource, remoteSource) = ConfigParamGenerator.generate(entries, path, localCodegen, remoteCodegen)
        val (localExtensionsSource, remoteExtensionsSource) =
            ExtensionFunctionGenerator.generate(entries, path, localCodegen, remoteCodegen)

        if (localSource.isNotEmpty()) {
            dir.resolve(ConfigParamGenerator.localFileName(path, localCodegen.objectName)).writeText(localSource)
        }
        if (remoteSource.isNotEmpty()) {
            dir.resolve(ConfigParamGenerator.remoteFileName(path, remoteCodegen.objectName)).writeText(remoteSource)
        }
        if (localExtensionsSource.isNotEmpty()) {
            dir.resolve(ExtensionFunctionGenerator.localFileName(path)).writeText(localExtensionsSource)
        }
        if (remoteExtensionsSource.isNotEmpty()) {
            dir.resolve(ExtensionFunctionGenerator.remoteFileName(path)).writeText(remoteExtensionsSource)
        }

        val local = entries.count { it.isLocal }
        val remote = entries.count { !it.isLocal }
        logger.lifecycle(
            "[featured] Generated ConfigParam + extensions: $local local, $remote remote flag(s) → ${dir.path}",
        )
    }
}
