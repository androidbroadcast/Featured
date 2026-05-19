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
        // Same-module collision: both LOCAL and REMOTE markers must appear so the origin is distinguishable.
        assertContains(ex.message ?: "", "LOCAL", message = "Message must name LOCAL kind")
        assertContains(ex.message ?: "", "REMOTE", message = "Message must name REMOTE kind")
        assertContains(ex.message ?: "", ":feature-checkout", message = "Message must name module path")
    }

    @Test
    fun `three modules colliding on same key all appear in error message`() {
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
        val ex = assertFailsWith<IllegalStateException> { validateUniqueKeys(manifests) }
        val msg = ex.message ?: ""
        assertContains(msg, "shared_flag", message = "Message must contain the duplicate key")
        assertContains(msg, ":feature-a", message = "Message must name :feature-a")
        assertContains(msg, ":feature-b", message = "Message must name :feature-b")
        assertContains(msg, ":feature-c", message = "Message must name :feature-c")
    }

    @Test
    fun `same module LOCAL and REMOTE collision shows both LOCAL and REMOTE not just module path twice`() {
        // Regression guard: before the fix the message read "':feature-checkout' and ':feature-checkout'"
        // with no kind information — indistinguishable from a cross-module collision with identical names.
        val manifests =
            listOf(
                FeaturedManifest(
                    schemaVersion = SCHEMA_VERSION,
                    modulePath = ":feature-checkout",
                    flags =
                        listOf(
                            booleanFlag(key = "show_avatar", kind = FlagKind.LOCAL),
                            booleanFlag(key = "show_avatar", kind = FlagKind.REMOTE),
                        ),
                ),
            )
        val ex = assertFailsWith<IllegalStateException> { validateUniqueKeys(manifests) }
        val msg = ex.message ?: ""
        assertContains(msg, "show_avatar", message = "Message must contain the duplicate key")
        assertContains(msg, "LOCAL", message = "Message must include LOCAL kind marker")
        assertContains(msg, "REMOTE", message = "Message must include REMOTE kind marker")
    }
}
