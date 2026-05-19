package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifestJson
import dev.androidbroadcast.featured.gradle.manifest.ValueType
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

private val PACKAGE_NAME_REGEX = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*")

// Accepted grammar for ENUM descriptor fields interpolated verbatim into generated Kotlin source.
// Untrusted manifest content from a malicious project dependency can inject Kotlin source via
// ENUM FQN or constant name — we reject anything that does not match before calling the generator.
private val KOTLIN_FQN_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
private val KOTLIN_IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

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
        val pkg = outputPackage.get()
        require(PACKAGE_NAME_REGEX.matches(pkg)) {
            "outputPackage '$pkg' is not a valid Kotlin package name."
        }

        val manifests =
            manifestFiles.files
                .map { file ->
                    try {
                        FeaturedManifestJson.decodeFromString<FeaturedManifest>(file.readText())
                    } catch (e: Exception) {
                        throw IllegalStateException(
                            "Failed to read or parse Featured manifest at '${file.path}': ${e.message}",
                            e,
                        )
                    }
                }

        validateUniqueKeys(manifests)
        validateFlagDescriptorIntegrity(manifests)

        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = manifests,
                packageName = pkg,
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
 * All duplicate keys are collected and reported in a single [IllegalStateException] so that
 * every conflict is visible without requiring repeated build invocations. Each origin includes
 * both the module path and the [FlagKind] so same-module LOCAL/REMOTE collisions are
 * distinguishable from cross-module collisions.
 *
 * Manifests are sorted by [FeaturedManifest.modulePath] internally before processing so that
 * the duplicate error message lists origins in a deterministic order regardless of the order
 * in which Gradle resolves manifest artifacts.
 *
 * @throws IllegalStateException listing every duplicate key and all conflicting origins.
 */
internal fun validateUniqueKeys(manifests: List<FeaturedManifest>) {
    val triples =
        manifests
            .sortedBy { it.modulePath }
            .flatMap { manifest ->
                manifest.flags.map { flag -> Triple(flag.key, manifest.modulePath, flag.kind) }
            }

    // Collect every key that appears more than once, together with all its origins.
    val duplicates =
        triples
            .groupBy { (key, _, _) -> key }
            .filter { (_, entries) -> entries.size > 1 }

    if (duplicates.isEmpty()) return

    val message =
        buildString {
            appendLine("Duplicate flag keys detected in aggregated Featured manifests:")
            duplicates.forEach { (key, entries) ->
                val origins = entries.joinToString(", ") { (_, path, kind) -> "'$path' ($kind)" }
                appendLine("  - '$key': declared in $origins")
            }
        }
    throw IllegalStateException(message.trimEnd())
}

/**
 * Validates the integrity of ENUM flag descriptors in [manifests] against Kotlin grammar before
 * passing them to the code generator.
 *
 * Threat model: a malicious build-script author of a project dependency declared via
 * `featuredAggregation(project(":evil"))` controls the contents of `featured-manifest.json`
 * and can supply arbitrary strings for `enumTypeFqn` and `defaultValue`. Both fields are
 * interpolated verbatim into the generated `.kt` file as Kotlin identifiers (not string
 * literals), so injecting `;`, `{`, `(`, or similar characters produces syntactically valid
 * Kotlin with arbitrary code that executes during the consuming project's `:compileKotlin`.
 *
 * We validate against Kotlin grammar here — single source of truth in the task — so the
 * generator can never emit unintended syntax regardless of what arrives in the manifest.
 *
 * @throws IllegalArgumentException when any ENUM flag has an invalid [enumTypeFqn] or
 *   [defaultValue], naming the offending key and module in the message.
 */
internal fun validateFlagDescriptorIntegrity(manifests: List<FeaturedManifest>) {
    manifests.forEach { manifest ->
        manifest.flags
            .filter { it.valueType == ValueType.ENUM }
            .forEach { flag ->
                requireNotNull(flag.enumTypeFqn) {
                    "enumTypeFqn must not be null for ENUM flag '${flag.key}' in module '${manifest.modulePath}'."
                }
                require(KOTLIN_FQN_REGEX.matches(flag.enumTypeFqn)) {
                    "Invalid enumTypeFqn '${flag.enumTypeFqn}' for flag '${flag.key}' in module '${manifest.modulePath}': " +
                        "must be a valid Kotlin fully-qualified name."
                }
                require(KOTLIN_IDENTIFIER_REGEX.matches(flag.defaultValue)) {
                    "Invalid ENUM defaultValue '${flag.defaultValue}' for flag '${flag.key}' in module '${manifest.modulePath}': " +
                        "must be a valid Kotlin identifier."
                }
            }
    }
}
