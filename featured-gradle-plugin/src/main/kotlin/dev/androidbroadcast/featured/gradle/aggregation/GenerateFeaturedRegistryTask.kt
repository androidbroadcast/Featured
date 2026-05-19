package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifestJson
import kotlinx.serialization.decodeFromString
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Aggregates `featured-manifest.json` files from all project dependencies declared via
 * `featuredAggregation(...)` and generates `GeneratedFeaturedRegistry.kt`.
 *
 * Registered by [FeaturedApplicationPlugin] under the name `generateFeaturedRegistry`.
 *
 * Validation: duplicate flag keys across modules (including LOCAL + REMOTE of the same module)
 * are rejected with an [IllegalStateException] naming both conflicting module paths.
 */
@CacheableTask
internal abstract class GenerateFeaturedRegistryTask : DefaultTask() {
    /**
     * The set of `featured-manifest.json` files resolved from `featuredAggregationClasspath`.
     *
     * [PathSensitivity.NONE] is used because only the file content matters for cache-key
     * computation — the artifact path varies across machines and build cache entries.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFiles: ConfigurableFileCollection

    /**
     * Package name written to the top of the generated source file.
     * Defaults to [FEATURED_REGISTRY_PACKAGE].
     */
    @get:Input
    abstract val outputPackage: Property<String>

    /**
     * Destination for the generated `GeneratedFeaturedRegistry.kt` source file.
     * Convention: `build/generated/featured/commonMain/GeneratedFeaturedRegistry.kt`.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val manifests =
            manifestFiles.files
                .map { file -> FeaturedManifestJson.decodeFromString<FeaturedManifest>(file.readText()) }

        validateUniqueKeys(manifests)

        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = manifests,
                packageName = outputPackage.get(),
            )

        val outFile = outputFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(source)

        val totalFlags = manifests.sumOf { it.flags.size }
        logger.lifecycle(
            "[featured] Generated registry with $totalFlags flag(s) from ${manifests.size} module(s) → ${outFile.path}",
        )
    }
}

/**
 * Validates that no two [FlagDescriptor][dev.androidbroadcast.featured.gradle.manifest.FlagDescriptor]
 * entries across all [manifests] share the same key.
 *
 * A flag declared in both `localFlags` and `remoteFlags` of the same module is treated as a
 * duplicate because each key produces exactly one `ConfigParam` in the registry.
 *
 * @throws IllegalStateException when a duplicate key is detected, naming both module paths and the key.
 */
internal fun validateUniqueKeys(manifests: List<FeaturedManifest>) {
    // Pair each descriptor with its module path for error reporting.
    val pairs = manifests.flatMap { manifest -> manifest.flags.map { flag -> flag.key to manifest.modulePath } }

    // Group by key; any group with more than one entry is a duplicate.
    pairs
        .groupBy { (key, _) -> key }
        .filter { (_, entries) -> entries.size > 1 }
        .forEach { (key, entries) ->
            val pathA = entries[0].second
            val pathB = entries[1].second
            throw IllegalStateException(
                "Duplicate flag key '$key': declared in '$pathA' and '$pathB'",
            )
        }
}
