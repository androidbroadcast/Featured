package dev.androidbroadcast.featured.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that reads the `featured { }` DSL extension and serialises all declared
 * flags to a pipe-delimited text file consumed by downstream generation tasks.
 *
 * Each output line has the format:
 * `key|defaultValue|type|moduleName|propertyName|flagType|description|category|expiresAt`
 *
 * where `propertyName` is the camelCase conversion of `key` (e.g. `dark_mode` → `darkMode`).
 *
 * Downstream tasks ([GenerateFlagRegistrarTask], [GenerateProguardRulesTask],
 * [GenerateConfigParamTask], etc.) declare [outputFile] as their `@InputFile` to
 * establish a proper task dependency and enable configuration-cache compatibility.
 */
@CacheableTask
public abstract class ResolveFlagsTask : DefaultTask() {
    /**
     * Pipe-delimited descriptors for flags declared in `featured { localFlags { } }`.
     * Each element: `key|defaultValue|type|description|category|expiresAt`.
     * Wired from [FeaturedExtension.localFlags] by [FeaturedPlugin].
     */
    @get:Input
    public abstract val localFlagDescriptors: ListProperty<String>

    /**
     * Pipe-delimited descriptors for flags declared in `featured { remoteFlags { } }`.
     * Same format as [localFlagDescriptors].
     */
    @get:Input
    public abstract val remoteFlagDescriptors: ListProperty<String>

    /** The Gradle module path (e.g. `:feature:checkout`), embedded in each output line. */
    @get:Input
    public abstract val moduleName: Property<String>

    /**
     * Output file containing all resolved [LocalFlagEntry] records, one per line.
     * Declare this as `@InputFile` in downstream tasks.
     */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun resolve() {
        val module = moduleName.get()
        val entries =
            buildList {
                localFlagDescriptors.get().forEach { add(parseDescriptor(it, module, LocalFlagEntry.FLAG_TYPE_LOCAL)) }
                remoteFlagDescriptors.get().forEach { add(parseDescriptor(it, module, LocalFlagEntry.FLAG_TYPE_REMOTE)) }
            }

        val out = outputFile.get().asFile
        out.parentFile?.mkdirs()
        out.writeText(
            entries.joinToString("\n") { e ->
                "${e.key}|${e.defaultValue}|${e.type}|${e.moduleName}" +
                    "|${e.propertyName}|${e.flagType}" +
                    "|${e.description.orEmpty()}|${e.category.orEmpty()}|${e.expiresAt.orEmpty()}"
            },
        )

        logger.lifecycle("[featured] Resolved ${entries.size} flag(s) in '$module'.")
        entries.forEach { e ->
            logger.lifecycle("  [${e.flagType}] ${e.key}: ${e.type} = ${e.defaultValue}")
        }
    }

    private fun parseDescriptor(
        descriptor: String,
        module: String,
        flagType: String,
    ): LocalFlagEntry {
        val parts = descriptor.split("|")
        val key = parts[0]
        return LocalFlagEntry(
            key = key,
            defaultValue = parts.getOrElse(1) { "" },
            type = parts.getOrElse(2) { "String" },
            moduleName = module,
            propertyName = key.toCamelCase(),
            flagType = flagType,
            description = parts.getOrNull(3)?.ifEmpty { null },
            category = parts.getOrNull(4)?.ifEmpty { null },
            expiresAt = parts.getOrNull(5)?.ifEmpty { null },
        )
    }
}
