package dev.androidbroadcast.featured.gradle.aggregation

/**
 * Name of the user-facing declarable Gradle configuration.
 * Consumers add dependencies here via `featuredAggregation(project(...))`.
 * Used by [FeaturedApplicationPlugin] to create the dependency scope.
 */
internal const val FEATURED_AGGREGATION_CONFIGURATION_NAME = "featuredAggregation"

/**
 * Name of the internal resolvable Gradle configuration.
 * Extends [FEATURED_AGGREGATION_CONFIGURATION_NAME] and carries the attribute contract
 * (`Usage = "featured-manifest"`, `schema-major = 1`) that Gradle uses to select the
 * `featuredManifest` outgoing variant from each producer module.
 */
internal const val FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME = "featuredAggregationClasspath"

/**
 * Task name registered by [FeaturedApplicationPlugin].
 * Running `./gradlew generateFeaturedRegistry` collects all manifests and writes the
 * generated Kotlin source to the output file.
 */
internal const val GENERATE_FEATURED_REGISTRY_TASK_NAME = "generateFeaturedRegistry"

/**
 * Package name emitted at the top of the generated `GeneratedFeaturedRegistry.kt` file.
 * Matches the package used by other Featured-generated sources in `commonMain`.
 */
internal const val FEATURED_REGISTRY_PACKAGE = "dev.androidbroadcast.featured.generated"

/**
 * Simple name of the generated Kotlin object and the output file (without `.kt` extension).
 * Used both as the object identifier in the generated source and as the output filename by
 * [GenerateFeaturedRegistryTask].
 */
internal const val FEATURED_REGISTRY_OBJECT = "GeneratedFeaturedRegistry"
