package dev.androidbroadcast.featured.gradle

import dev.androidbroadcast.featured.gradle.aggregation.FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME
import dev.androidbroadcast.featured.gradle.aggregation.FEATURED_AGGREGATION_CONFIGURATION_NAME
import dev.androidbroadcast.featured.gradle.aggregation.FEATURED_REGISTRY_PACKAGE
import dev.androidbroadcast.featured.gradle.aggregation.GENERATE_FEATURED_REGISTRY_TASK_NAME
import dev.androidbroadcast.featured.gradle.aggregation.GenerateFeaturedRegistryTask
import dev.androidbroadcast.featured.gradle.manifest.FEATURED_MANIFEST_USAGE
import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.schemaMajorAttr
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage

/**
 * Gradle plugin ID: `dev.androidbroadcast.featured.application`.
 *
 * Aggregates `featured-manifest.json` artifacts from all project dependencies declared via
 * `featuredAggregation(project(...))` and generates a unified
 * `object GeneratedFeaturedRegistry { val all: List<ConfigParam<*>> }` Kotlin source file.
 *
 * Apply this plugin alongside `dev.androidbroadcast.featured` in the application or aggregator
 * module:
 * ```kotlin
 * plugins {
 *     id("dev.androidbroadcast.featured")
 *     id("dev.androidbroadcast.featured.application")
 * }
 *
 * dependencies {
 *     featuredAggregation(project(":feature:checkout"))
 *     featuredAggregation(project(":feature:profile"))
 * }
 * ```
 *
 * The generated file is written to
 * `build/generated/featured/registry/GeneratedFeaturedRegistry.kt` (a dedicated directory, distinct
 * from the per-module `generated/featured/commonMain` used by `generateConfigParam`, so the two
 * tasks never overlap when a module applies both plugins). The plugin auto-wires that directory into
 * the consuming module's compilation (KMP `commonMain`, Kotlin/JVM `main`, or the AGP `main` Kotlin
 * source set) — no manual `srcDir` / `dependsOn` is required.
 *
 * **Enum flag classpath requirement.** A `featuredAggregation(project(":feature:foo"))` dependency
 * resolves only the `featured-manifest` Gradle variant — it does NOT put the producer's enum types
 * on the consumer's compile classpath. If `:feature:foo` declares an `enum` flag whose type lives
 * in `:feature:foo`'s source set, the application module must add a regular runtime dependency on
 * the same module so the enum class is visible at compile time:
 * ```kotlin
 * dependencies {
 *     featuredAggregation(project(":feature:foo"))
 *     implementation(project(":feature:foo"))    // required for enum flag types
 * }
 * ```
 * For modules that declare only primitive flags (Boolean / Int / Long / Float / Double / String),
 * the `featuredAggregation` line alone is sufficient.
 *
 * Min Gradle version: 8.5+ (`configurations.dependencyScope()` / `.resolvable()` API).
 */
@Suppress("UnstableApiUsage")
internal class FeaturedApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Register the schemaMajorAttr in the project's attribute schema. This is idempotent —
        // if FeaturedPlugin is also applied it calls the same registration first.
        target.dependencies.attributesSchema.attribute(schemaMajorAttr)

        // User-facing declarable scope: consumers add project() dependencies here.
        val declarable =
            target.configurations.dependencyScope(
                FEATURED_AGGREGATION_CONFIGURATION_NAME,
            ) { cfg ->
                cfg.description =
                    "Project dependencies whose featured-manifest.json should be aggregated into GeneratedFeaturedRegistry."
            }

        // Internal resolvable classpath that carries the attribute contract used by Gradle's
        // variant selection to match the `featuredManifest` consumable configuration published
        // by each producer module applying `dev.androidbroadcast.featured`.
        val classpath =
            target.configurations.resolvable(
                FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME,
            ) { cfg ->
                cfg.description =
                    "Internal classpath resolving featured-manifest.json artifacts from featuredAggregation."
                cfg.extendsFrom(declarable.get())
                cfg.attributes { attrs ->
                    attrs.attribute(
                        Usage.USAGE_ATTRIBUTE,
                        target.objects.named(Usage::class.java, FEATURED_MANIFEST_USAGE),
                    )
                    // Mirror the schema-major attribute declared on the producer side so that Gradle's
                    // variant selection picks exactly the schema-v1 manifests.
                    attrs.attribute(schemaMajorAttr, SCHEMA_VERSION)
                }
            }

        val registryTask =
            target.tasks.register(
                GENERATE_FEATURED_REGISTRY_TASK_NAME,
                GenerateFeaturedRegistryTask::class.java,
            ) { task ->
                task.group = "featured"
                task.description =
                    "Aggregates featured-manifest.json artifacts and generates GeneratedFeaturedRegistry.kt."
                // Lazy artifact view — resolved at execution time, CC-compatible.
                task.manifestFiles.from(
                    classpath.map { it.incoming.artifactView { view -> view.isLenient = false }.files },
                )
                task.outputPackage.set(FEATURED_REGISTRY_PACKAGE)
                // Own dedicated directory — NOT the per-module `generated/featured/commonMain` that
                // `generateConfigParam` uses. A module that applies both `dev.androidbroadcast.featured`
                // and this aggregator would otherwise have two tasks declaring the same @OutputDirectory,
                // which Gradle rejects as overlapping outputs.
                task.outputDir.convention(
                    target.layout.buildDirectory.dir("generated/featured/registry"),
                )
            }

        // Auto-wire the generated registry source into compilation. KMP / Kotlin-JVM receive the
        // task-carrying @OutputDirectory provider `registryTask.flatMap { it.outputDir }`; passing it
        // to `srcDir(Provider)` records the registry task as the dir's builder, so Gradle auto-infers
        // the dependency for EVERY consumer (compileAndroidMain, compileKotlinJvm, sourcesJar,
        // metadata, …) with no name-matched dependsOn. AGP cannot take a provider (its source set
        // rejects one at configuration time), so it gets a resolved File from the pure layout path
        // plus the explicit [registryTask] ordering. Only the matching branch fires.
        val registryDir = registryTask.flatMap { it.outputDir }
        val registryDirFile =
            target.layout.buildDirectory
                .dir("generated/featured/registry")
                .get()
                .asFile
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            wireGeneratedSourcesToKmp(target, registryDir)
        }
        target.plugins.withId("org.jetbrains.kotlin.jvm") {
            wireGeneratedSourcesToKotlinJvm(target, registryDir)
        }
        target.plugins.withId("com.android.application") {
            wireGeneratedSourcesToAndroid(target, registryDirFile, registryTask)
        }
        target.plugins.withId("com.android.library") {
            wireGeneratedSourcesToAndroid(target, registryDirFile, registryTask)
        }
    }
}
