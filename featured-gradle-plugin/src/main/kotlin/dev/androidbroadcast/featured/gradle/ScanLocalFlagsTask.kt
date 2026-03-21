package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that scans Kotlin source files for `@LocalFlag`-annotated
 * `ConfigParam` declarations in the owning module.
 *
 * Results are written to [outputFile] as a line-delimited text report and exposed
 * via [scannedEntries] for in-process consumers. Downstream generation tasks should
 * declare `inputs.files(scanTask.flatMap { it.outputFile })` to establish a proper
 * task dependency and enable configuration-cache compatibility.
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
     * Output file containing the scanned [LocalFlagEntry] records, one per line in
     * `key|defaultValue|type|moduleName` format.
     *
     * Downstream tasks should declare this file as an input to establish a proper
     * task dependency rather than reading [scannedEntries] directly.
     */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    /**
     * Entries found during the last [scan] execution.
     * Empty before the task has run.
     *
     * Marked `@Internal` so Gradle's task property validator does not require this
     * in-memory field to be declared as an input or output. Downstream tasks should
     * use [outputFile] to declare a proper file-based dependency instead.
     */
    @get:Internal
    public var scannedEntries: List<LocalFlagEntry> = emptyList()
        private set

    @TaskAction
    public fun scan() {
        val entries =
            sourceFiles.flatMap { file ->
                LocalFlagScanner.scan(file.readText(), moduleName.get())
            }
        scannedEntries = entries

        // Write structured output for downstream tasks.
        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(
            entries.joinToString("\n") { e ->
                "${e.key}|${e.defaultValue}|${e.type}|${e.moduleName}"
            },
        )

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
