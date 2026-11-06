package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.PACKAGE_NAME_REGEX
import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifestJson
import dev.androidbroadcast.featured.gradle.manifest.ValueType
import kotlinx.serialization.decodeFromString
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

// Accepted grammar for ENUM descriptor fields interpolated verbatim into generated Kotlin source.
// Untrusted manifest content from a malicious project dependency can inject Kotlin source via
// ENUM FQN or constant name — we reject anything that does not match before calling the generator.
private val KOTLIN_FQN_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
private val KOTLIN_IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

// Accepted grammars for primitive defaultValue fields interpolated verbatim into the generated
// Kotlin source. Malicious manifests can embed arbitrary Kotlin by supplying e.g. an INT value
// that contains method-call suffixes or a BOOLEAN value with an extra statement appended.
// Each regex matches only the literal forms that Kotlin accepts for the respective numeric type.
private val BOOLEAN_LITERAL_REGEX = Regex("true|false")
private val INT_LITERAL_REGEX = Regex("-?\\d+")
private val LONG_LITERAL_REGEX = Regex("-?\\d+")
private val FLOAT_LITERAL_REGEX = Regex("-?\\d+(\\.\\d+)?([eE]-?\\d+)?")
private val DOUBLE_LITERAL_REGEX = Regex("-?\\d+(\\.\\d+)?([eE]-?\\d+)?")

/**
 * Aggregates `featured-manifest.json` files from all project dependencies declared via
 * `featuredAggregation(...)` and generates `GeneratedFeaturedRegistry.kt`.
 *
 * Registered by [FeaturedApplicationPlugin] under the name `generateFeaturedRegistry`.
 *
 * Duplicate public names are not rejected. A local flag and a remote flag may share a name.
 * The same scope and key remains last-write-wins at runtime.
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
     * Output directory receiving the generated `GeneratedFeaturedRegistry.kt` source file.
     * Convention (set by [FeaturedApplicationPlugin]): `build/generated/featured/registry/`.
     *
     * A dedicated `registry/` directory — not the per-module `commonMain/` dir used by
     * `generateConfigParam` — avoids an overlapping-output conflict when a module applies both
     * `dev.androidbroadcast.featured` and `dev.androidbroadcast.featured.application`.
     *
     * This is a [DirectoryProperty] (not a single-file output) so [FeaturedApplicationPlugin] can
     * pass the task-carrying provider straight to `srcDir(Provider)`; Gradle then auto-infers the
     * task dependency for every KMP/Kotlin-JVM consumer (compile*, sourcesJar, metadata, …).
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

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

        val outFile = outputDir.get().asFile.resolve("$FEATURED_REGISTRY_OBJECT.kt")
        outFile.parentFile.mkdirs()
        outFile.writeText(source)

        val totalFlags = manifests.sumOf { it.flags.size }
        logger.lifecycle(
            "[featured] Generated registry with $totalFlags flag(s) from ${manifests.size} module(s) → ${outFile.path}",
        )
    }
}

/**
 * Duplicate public names are not an error.
 *
 * A flag in `localFlags` and a flag in `remoteFlags` may share a key. The registry emits both,
 * each with the scope of its [dev.androidbroadcast.featured.gradle.manifest.FlagKind]. The same
 * scope and key stays last-write-wins in the snapshot and warm-set, including across modules.
 * This function remains so the registry task's validation step has an explicit no-op rather than
 * a silent deletion of the check.
 */
@Suppress("UnusedParameter")
internal fun validateUniqueKeys(manifests: List<FeaturedManifest>) {
    // Names are not unique across containers. Isolation is FlagScope, not a DSL error.
}

/**
 * Validates the integrity of all flag descriptors in [manifests] against Kotlin literal grammars
 * before passing them to the code generator.
 *
 * Threat model: a malicious build-script author of a project dependency declared via
 * `featuredAggregation(project(":evil"))` controls the contents of `featured-manifest.json`
 * and can supply arbitrary strings for `enumTypeFqn` and `defaultValue`. These fields are
 * interpolated verbatim into the generated `.kt` file, so injecting `;`, `{`, `(`, or similar
 * characters produces syntactically valid Kotlin with arbitrary code that executes during the
 * consuming project's `:compileKotlin`.
 *
 * We validate against Kotlin grammar here — single source of truth in the task — so the
 * generator can never emit unintended syntax regardless of what arrives in the manifest.
 *
 * @throws IllegalArgumentException when any flag has an invalid [defaultValue] (or, for ENUM,
 *   an invalid [enumTypeFqn]), naming the offending key and module in the message.
 */
internal fun validateFlagDescriptorIntegrity(manifests: List<FeaturedManifest>) {
    manifests.forEach { manifest ->
        manifest.flags.forEach { flag ->
            when (flag.valueType) {
                ValueType.BOOLEAN -> {
                    require(BOOLEAN_LITERAL_REGEX.matches(flag.defaultValue)) {
                        "Invalid Boolean defaultValue '${flag.defaultValue}' for flag '${flag.key}' " +
                            "in module '${manifest.modulePath}': must be 'true' or 'false'."
                    }
                }

                ValueType.INT -> {
                    require(INT_LITERAL_REGEX.matches(flag.defaultValue)) {
                        "Invalid Int defaultValue '${flag.defaultValue}' for flag '${flag.key}' " +
                            "in module '${manifest.modulePath}': must be an integer literal (digits, optional leading minus)."
                    }
                }

                ValueType.LONG -> {
                    require(LONG_LITERAL_REGEX.matches(flag.defaultValue)) {
                        "Invalid Long defaultValue '${flag.defaultValue}' for flag '${flag.key}' " +
                            "in module '${manifest.modulePath}': must be an integer literal (digits, optional leading minus)."
                    }
                }

                ValueType.FLOAT -> {
                    require(FLOAT_LITERAL_REGEX.matches(flag.defaultValue)) {
                        "Invalid Float defaultValue '${flag.defaultValue}' for flag '${flag.key}' " +
                            "in module '${manifest.modulePath}': must be a numeric literal (digits, optional decimal and exponent)."
                    }
                }

                ValueType.DOUBLE -> {
                    require(DOUBLE_LITERAL_REGEX.matches(flag.defaultValue)) {
                        "Invalid Double defaultValue '${flag.defaultValue}' for flag '${flag.key}' " +
                            "in module '${manifest.modulePath}': must be a numeric literal (digits, optional decimal and exponent)."
                    }
                }

                ValueType.STRING -> {
                    // STRING values are escaped via escapeKotlinString in the generator — no validation needed.
                }

                ValueType.ENUM -> {
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
    }
}
