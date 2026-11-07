package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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
 * All files are written to [outputDir]. The shared task writes the base default to
 * `build/generated/featured/commonMain/` and is wired into KMP `commonMain` and Kotlin/JVM
 * `main`. Android application and library compilations do not consume that directory: each
 * build type has its own task writing `build/generated/featured/buildType/<buildType>/`, with
 * local [LocalFlagEntry.defaultValue] replaced by that build type's effective default.
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

    /**
     * Android build type whose effective default replaces each local entry's base default.
     * Absent on the shared task, which always emits the base default.
     */
    @get:Input
    @get:Optional
    public abstract val buildType: Property<String>

    /**
     * Build-type overrides, one `key|buildType|serializedValue` line per [FlagSpec.defaultFor].
     * Empty means every build type uses the base. A change here invalidates this task even when
     * the base default in [flagsFile] did not change.
     */
    @get:Input
    public abstract val buildTypeDefaultOverrides: ListProperty<String>

    init {
        // Conventions cover direct task usage; FeaturedPlugin always wires the resolved
        // DSL values explicitly.
        localPackageName.convention(ConfigParamGenerator.DEFAULT_PACKAGE)
        remotePackageName.convention(ConfigParamGenerator.DEFAULT_PACKAGE)
        localVisibility.convention(FeaturedVisibility.INTERNAL)
        remoteVisibility.convention(FeaturedVisibility.INTERNAL)
        buildTypeDefaultOverrides.convention(emptyList())
    }

    @TaskAction
    public fun generate() {
        val parsed = flagsFile.parseLocalFlagEntries()
        val entries =
            buildType.orNull?.let { name ->
                parsed.withEffectiveDefaults(name, buildTypeDefaultOverrides.get())
            } ?: parsed
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
