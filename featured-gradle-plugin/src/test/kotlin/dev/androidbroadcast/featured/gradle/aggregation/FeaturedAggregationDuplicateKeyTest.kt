package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FlagDescriptor
import dev.androidbroadcast.featured.gradle.manifest.FlagKind
import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.ValueType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

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
    fun `duplicate key across two modules throws with both module paths`() {
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
        val ex = assertFailsWith<IllegalStateException> { validateUniqueKeys(manifests) }
        assertContains(ex.message ?: "", "dark_mode", message = "Message must contain the duplicate key")
        assertContains(ex.message ?: "", ":feature-a", message = "Message must name first module path")
        assertContains(ex.message ?: "", ":feature-b", message = "Message must name second module path")
    }

    @Test
    fun `same key in LOCAL and REMOTE of same module is a duplicate`() {
        // A single module declaring the same key in both localFlags and remoteFlags.
        val manifests =
            listOf(
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-checkout",
                    flags =
                        listOf(
                            booleanFlag(key = "checkout_mode", kind = FlagKind.LOCAL),
                            booleanFlag(key = "checkout_mode", kind = FlagKind.REMOTE),
                        ),
                ),
            )
        val ex = assertFailsWith<IllegalStateException> { validateUniqueKeys(manifests) }
        assertContains(ex.message ?: "", "checkout_mode", message = "Message must contain the duplicate key")
        // Both paths are the same module; message still names the module path twice
        assertContains(ex.message ?: "", ":feature-checkout", message = "Message must name module path")
    }
}
