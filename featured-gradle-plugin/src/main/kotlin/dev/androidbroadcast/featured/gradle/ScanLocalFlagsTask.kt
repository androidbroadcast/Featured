package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that scans Kotlin source files for `@LocalFlag`-annotated
 * `ConfigParam` declarations in the owning module.
 *
 * Results are logged and made available via [scannedEntries] after task execution.
 * Downstream generation tasks may depend on this task and read [scannedEntries].
 */
public abstract class ScanLocalFlagsTask : DefaultTask() {
    /**
     * Kotlin source files to scan. Wired automatically from the module's source sets
     * by [FeaturedPlugin].
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sourceFiles: ConfigurableFileCollection

    /**
     * The Gradle module path (e.g. `:core`, `:app`) used to populate
     * [LocalFlagEntry.moduleName] in scan results.
     */
    @get:Input
    public abstract val moduleName: Property<String>

    /**
     * Entries found during the last [scan] execution.
     * Empty before the task has run.
     */
    public var scannedEntries: List<LocalFlagEntry> = emptyList()
        private set

    @TaskAction
    public fun scan() {
        val entries = sourceFiles
            .filter { it.extension == "kt" }
            .flatMap { file ->
                LocalFlagScanner.scan(file.readText(), moduleName.get())
            }
        scannedEntries = entries

        if (entries.isEmpty()) {
            logger.lifecycle("[@LocalFlag scan] No @LocalFlag declarations found in module '${moduleName.get()}'.")
        } else {
            logger.lifecycle("[@LocalFlag scan] Found ${entries.size} @LocalFlag declaration(s) in '${moduleName.get()}':")
            entries.forEach { entry ->
                logger.lifecycle("  - key='${entry.key}', defaultValue='${entry.defaultValue}', type=${entry.type}")
            }
        }
    }
}
