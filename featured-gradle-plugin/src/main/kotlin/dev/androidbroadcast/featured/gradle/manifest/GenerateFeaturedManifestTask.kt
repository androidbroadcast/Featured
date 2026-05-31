package dev.androidbroadcast.featured.gradle.manifest

import dev.androidbroadcast.featured.gradle.LocalFlagEntry
import dev.androidbroadcast.featured.gradle.parseLocalFlagEntries
import kotlinx.serialization.encodeToString
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
 * Generates the per-module `featured-manifest.json` artifact consumed by the PR-B aggregator.
 *
 * Reads the flag report from [flagsFile] (produced by `resolveFeatureFlags`), maps each
 * [LocalFlagEntry] to a [FlagDescriptor], and writes the result as a JSON document to
 * [outputFile].
 *
 * The output file is published via the `featuredManifest` consumable Gradle configuration
 * so that downstream aggregator modules can resolve all per-module manifests through
 * normal dependency resolution.
 */
@CacheableTask
internal abstract class GenerateFeaturedManifestTask : DefaultTask() {
    /**
     * The pipe-delimited flag report produced by `resolveFeatureFlags`.
     *
     * [PathSensitivity.NONE] is used because this is a generated intermediate file whose
     * absolute path varies across machines and build directories. Only the file content
     * matters for cache key computation — the path itself is irrelevant to correctness.
     * This matches the sensitivity used by all other Generate* tasks that consume the same
     * flags.txt file.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val flagsFile: RegularFileProperty

    /**
     * Gradle `Project.path` for this module (e.g. `":feature:checkout"`, `":"`).
     *
     * Set as a snapshot string at configuration time (not as a lazy provider) to ensure
     * Configuration Cache compliance — `Project` instances must not be captured by task
     * state at execution time.
     */
    @get:Input
    abstract val modulePath: Property<String>

    /**
     * Output path for the generated `featured-manifest.json`.
     *
     * The convention `build/featured/featured-manifest.json` is wired by [FeaturedPlugin];
     * it keeps all Featured build outputs under a single directory alongside `flags.txt`
     * and `proguard-featured.pro`.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val path = modulePath.get()
        require(path.startsWith(":")) {
            "modulePath must be a Gradle path starting with ':', was '$path'"
        }

        val entries = flagsFile.parseLocalFlagEntries()
        val descriptors = entries.map { entry -> entry.toFlagDescriptor() }
        val manifest =
            FeaturedManifest(
                schemaVersion = SCHEMA_VERSION,
                modulePath = path,
                flags = descriptors,
            )

        val outFile = outputFile.get().asFile
        outFile.parentFile?.mkdirs()
        outFile.writeText(FeaturedManifestJson.encodeToString(manifest))

        logger.lifecycle(
            "[featured] Generated manifest with ${descriptors.size} flag(s) → ${outFile.path}",
        )
    }
}

internal fun LocalFlagEntry.toFlagDescriptor(): FlagDescriptor {
    val kind = if (isLocal) FlagKind.LOCAL else FlagKind.REMOTE

    val valueType =
        if (isEnum) {
            ValueType.ENUM
        } else {
            when (type) {
                "Boolean" -> ValueType.BOOLEAN

                "Int" -> ValueType.INT

                "Long" -> ValueType.LONG

                "Float" -> ValueType.FLOAT

                "Double" -> ValueType.DOUBLE

                "String" -> ValueType.STRING

                // Explicit error with key name — ValueType.valueOf(type.uppercase()) would produce
                // a cryptic "No enum constant" message with no context about which flag failed.
                else -> error("Unsupported flag value type '$type' for key '$key'")
            }
        }

    val resolvedDefault =
        when (valueType) {
            // FlagContainer.string() wraps the default in escaped quotes: defaultValue = "\"hello\"".
            // ScanResultParser stores it verbatim. Strip the surrounding quotes here so the aggregator
            // can use the bare value without further processing.
            ValueType.STRING -> defaultValue.removeSurrounding("\"")

            // ConfigParamGenerator writes qualified form "EnumType.VARIANT"; only the constant
            // name is useful for the aggregator (it calls enumValueOf<T>(defaultValue)).
            ValueType.ENUM -> defaultValue.substringAfterLast('.')

            else -> defaultValue
        }

    return FlagDescriptor(
        key = key,
        propertyName = propertyName,
        kind = kind,
        valueType = valueType,
        defaultValue = resolvedDefault,
        enumTypeFqn = type.takeIf { isEnum },
        description = description,
        category = category,
        expiresAt = expiresAt,
    )
}
