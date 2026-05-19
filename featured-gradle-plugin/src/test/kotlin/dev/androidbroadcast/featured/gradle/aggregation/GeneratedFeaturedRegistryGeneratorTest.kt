package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FlagDescriptor
import dev.androidbroadcast.featured.gradle.manifest.FlagKind
import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.ValueType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedFeaturedRegistryGeneratorTest {
    private fun manifest(
        modulePath: String,
        vararg flags: FlagDescriptor,
    ): FeaturedManifest =
        FeaturedManifest(
            schemaVersion = SCHEMA_VERSION,
            modulePath = modulePath,
            flags = flags.toList(),
        )

    private fun flag(
        key: String,
        valueType: ValueType,
        defaultValue: String,
        kind: FlagKind = FlagKind.LOCAL,
        enumTypeFqn: String? = null,
        description: String? = null,
        category: String? = null,
    ) = FlagDescriptor(
        key = key,
        propertyName = key,
        kind = kind,
        valueType = valueType,
        defaultValue = defaultValue,
        enumTypeFqn = enumTypeFqn,
        description = description,
        category = category,
    )

    @Test
    fun `empty manifests list produces emptyList body`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = emptyList(),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "emptyList()")
        assertFalse(source.contains("listOf("), "Expected no listOf when empty")
    }

    @Test
    fun `single BOOLEAN local flag emits correct ConfigParam`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "dark_mode", valueType = ValueType.BOOLEAN, defaultValue = "false"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "ConfigParam<Boolean>(key = \"dark_mode\", defaultValue = false)")
    }

    @Test
    fun `LONG suffix is L`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "timeout", valueType = ValueType.LONG, defaultValue = "123"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "defaultValue = 123L")
    }

    @Test
    fun `FLOAT suffix is f`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "ratio", valueType = ValueType.FLOAT, defaultValue = "1.5"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "defaultValue = 1.5f")
    }

    @Test
    fun `DOUBLE emits raw value`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "pi", valueType = ValueType.DOUBLE, defaultValue = "3.14"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "defaultValue = 3.14")
        assertFalse(source.contains("3.14f"), "DOUBLE must not have f suffix")
        assertFalse(source.contains("3.14L"), "DOUBLE must not have L suffix")
    }

    @Test
    fun `INT emits raw value`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "retries", valueType = ValueType.INT, defaultValue = "3"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "defaultValue = 3")
        assertFalse(source.contains("3L"), "INT must not have L suffix")
    }

    @Test
    fun `STRING re-wraps bare value in quotes`() {
        // Producer stores bare value: "hello world" (no surrounding quotes)
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "label", valueType = ValueType.STRING, defaultValue = "hello world"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "defaultValue = \"hello world\"")
    }

    @Test
    fun `STRING escapes embedded double quotes`() {
        // Producer stores bare: say "hi" — generator must emit: "say \"hi\""
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "greeting", valueType = ValueType.STRING, defaultValue = """say "hi""""))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, """defaultValue = "say \"hi\"""")
    }

    @Test
    fun `ENUM emits enumTypeFqn dot constant as default and type arg`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(
                            ":feature",
                            flag(
                                key = "checkout_variant",
                                valueType = ValueType.ENUM,
                                defaultValue = "LEGACY",
                                enumTypeFqn = "com.example.CheckoutVariant",
                            ),
                        ),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "ConfigParam<com.example.CheckoutVariant>")
        assertContains(source, "defaultValue = com.example.CheckoutVariant.LEGACY")
    }

    @Test
    fun `multi-module input lists all flags`() {
        val moduleA =
            manifest(
                ":feature-a",
                flag(key = "flag_a1", valueType = ValueType.BOOLEAN, defaultValue = "true"),
                flag(key = "flag_a2", valueType = ValueType.INT, defaultValue = "1"),
            )
        val moduleB =
            manifest(
                ":feature-b",
                flag(key = "flag_b1", valueType = ValueType.STRING, defaultValue = "hello"),
                flag(key = "flag_b2", valueType = ValueType.LONG, defaultValue = "99"),
            )
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(moduleA, moduleB),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "flag_a1")
        assertContains(source, "flag_a2")
        assertContains(source, "flag_b1")
        assertContains(source, "flag_b2")
    }

    @Test
    fun `stable order manifests in B-A input produce flags sorted by modulePath then key`() {
        // Manifests passed in [B, A] order — output must be A's flags first, then B's.
        val moduleA =
            manifest(
                ":feature-a",
                flag(key = "z_flag", valueType = ValueType.BOOLEAN, defaultValue = "false"),
                flag(key = "a_flag", valueType = ValueType.BOOLEAN, defaultValue = "true"),
            )
        val moduleB =
            manifest(
                ":feature-b",
                flag(key = "m_flag", valueType = ValueType.INT, defaultValue = "5"),
            )
        // Pass B before A intentionally
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(moduleB, moduleA),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        val aFlagPos = source.indexOf("a_flag")
        val zFlagPos = source.indexOf("z_flag")
        val mFlagPos = source.indexOf("m_flag")

        // :feature-a < :feature-b alphabetically; within :feature-a, a_flag < z_flag
        assertTrue(aFlagPos < zFlagPos, "a_flag must appear before z_flag (within :feature-a)")
        assertTrue(zFlagPos < mFlagPos, "z_flag (:feature-a) must appear before m_flag (:feature-b)")
    }

    @Test
    fun `optional description is emitted when non-null`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(
                            ":app",
                            flag(
                                key = "my_flag",
                                valueType = ValueType.BOOLEAN,
                                defaultValue = "true",
                                description = "Controls the widget",
                            ),
                        ),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "description = \"Controls the widget\"")
    }

    @Test
    fun `null description is omitted from ConfigParam args`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(":app", flag(key = "my_flag", valueType = ValueType.BOOLEAN, defaultValue = "false")),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertFalse(source.contains("description ="), "description must be absent when null")
    }

    @Test
    fun `since parameter is never emitted`() {
        // Manifest schema v1 has no since field; ConfigParam accepts it but we never emit it
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(":app", flag(key = "feature", valueType = ValueType.BOOLEAN, defaultValue = "true")),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertFalse(source.contains("since ="), "since must never be emitted")
    }
}
