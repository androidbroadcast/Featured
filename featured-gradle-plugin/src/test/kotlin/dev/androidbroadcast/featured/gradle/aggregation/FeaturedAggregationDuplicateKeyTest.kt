package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FlagDescriptor
import dev.androidbroadcast.featured.gradle.manifest.FlagKind
import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.ValueType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FeaturedAggregationDuplicateKeyTest {
    private fun booleanFlag(
        key: String,
        kind: FlagKind = FlagKind.LOCAL,
    ) = FlagDescriptor(
        key = key,
        propertyName = key,
        kind = kind,
        valueType = ValueType.BOOLEAN,
        defaultValue = "false",
    )

    @Test
    fun `no error for unique keys across modules`() {
        val manifests =
            listOf(
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-a",
                    flags = listOf(booleanFlag("dark_mode")),
                ),
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-b",
                    flags = listOf(booleanFlag("show_banner")),
                ),
            )
        // Should not throw
        validateUniqueKeys(manifests)
    }

    @Test
    fun `duplicate key across two modules is not an error`() {
        val manifests =
            listOf(
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-a",
                    flags = listOf(booleanFlag("dark_mode")),
                ),
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-b",
                    flags = listOf(booleanFlag("dark_mode")),
                ),
            )
        validateUniqueKeys(manifests)
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = manifests,
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertEquals(2, Regex("key = \"dark_mode\"").findAll(source).count())
    }

    @Test
    fun `same key in LOCAL and REMOTE of same module is not an error`() {
        val manifests =
            listOf(
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-checkout",
                    flags =
                        listOf(
                            booleanFlag(key = "FEATURE_X", kind = FlagKind.LOCAL),
                            booleanFlag(key = "FEATURE_X", kind = FlagKind.REMOTE),
                        ),
                ),
            )
        validateUniqueKeys(manifests)
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = manifests,
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "scope = FlagScope.LOCAL")
        assertContains(source, "scope = FlagScope.REMOTE")
        assertContains(source, "key = \"FEATURE_X\"")
        assertEquals(2, Regex("key = \"FEATURE_X\"").findAll(source).count())
    }

    @Test
    fun `same scope duplicate across modules is emitted not rejected`() {
        val manifests =
            listOf(
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-a",
                    flags = listOf(booleanFlag("shared_flag")),
                ),
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-b",
                    flags = listOf(booleanFlag("shared_flag")),
                ),
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-c",
                    flags = listOf(booleanFlag("shared_flag")),
                ),
            )
        validateUniqueKeys(manifests)
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = manifests,
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertEquals(3, Regex("key = \"shared_flag\"").findAll(source).count())
    }
}
