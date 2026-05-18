package dev.androidbroadcast.featured.gradle.manifest

import org.gradle.api.attributes.Attribute

internal const val GENERATE_FEATURED_MANIFEST_TASK_NAME = "generateFeaturedManifest"
internal const val FEATURED_MANIFEST_CONFIGURATION_NAME = "featuredManifest"
internal const val FEATURED_MANIFEST_USAGE = "featured-manifest"
internal const val SCHEMA_MAJOR_ATTRIBUTE_NAME = "dev.androidbroadcast.featured.schema-major"

/**
 * Gradle attribute that carries the major version of the Featured manifest schema.
 *
 * The attribute is declared as `Attribute<Int>` for ergonomic use from Kotlin call sites
 * (`attribute(schemaMajorAttr, SCHEMA_VERSION)`). Under the hood Kotlin maps `Int` in a
 * generic position to `java.lang.Integer`, which is the JVM boxed type Gradle uses for
 * attribute equality. [Int.javaObjectType] (`Int::class.javaObjectType`) returns exactly
 * `Integer.class`, so this is wire-compatible with a Java-side `Attribute<Integer>`.
 *
 * The consumer (PR B aggregator) must declare the same [Attribute] instance — sharing
 * this constant guarantees a single Attribute object.
 */
internal val schemaMajorAttr: Attribute<Int> =
    Attribute.of(SCHEMA_MAJOR_ATTRIBUTE_NAME, Int::class.javaObjectType)
