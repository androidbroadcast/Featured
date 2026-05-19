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
        assertContains(source, "enumConstants = kotlin.enumValues<com.example.CheckoutVariant>().toList()")
    }

    @Test
    fun `BOOLEAN flag does not emit enumConstants`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "dark_mode", valueType = ValueType.BOOLEAN, defaultValue = "false"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertFalse(source.contains("enumConstants"), "enumConstants must not appear for non-enum types")
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

    // NIT 4 — escape paths for STRING default: backslash and dollar sign

    @Test
    fun `STRING default with backslash is escaped`() {
        // Producer stores bare: path\to\file — generator must emit: "path\\to\\file"
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(manifest(":app", flag(key = "path_flag", valueType = ValueType.STRING, defaultValue = """path\to\file"""))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, """defaultValue = "path\\to\\file"""")
    }

    @Test
    fun `STRING default with dollar sign is escaped to prevent template interpolation`() {
        // Producer stores bare: price $9.99 — generator must emit: "price ${'$'}9.99"
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(manifest(":app", flag(key = "price_flag", valueType = ValueType.STRING, defaultValue = "price \$9.99"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        // The generated source must contain the Kotlin-safe form that prevents interpolation.
        assertContains(source, "price \${'\$'}9.99")
    }

    @Test
    fun `key containing double quote is escaped in generated source`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(manifest(":app", flag(key = """dark"mode""", valueType = ValueType.BOOLEAN, defaultValue = "false"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        // Generated key must have the quote escaped: key = "dark\"mode"
        assertContains(source, """key = "dark\"mode"""")
    }

    @Test
    fun `description containing dollar sign is escaped`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(
                            ":app",
                            flag(
                                key = "promo",
                                valueType = ValueType.BOOLEAN,
                                defaultValue = "true",
                                description = "Price: \$9.99",
                            ),
                        ),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "Price: \${'\$'}9.99")
    }

    // Fix 1 — newline / tab escape in STRING default and description

    @Test
    fun `STRING default with newline is escaped to backslash-n`() {
        // Producer stores a value with a real newline character; generated source must not contain a raw newline.
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "multiline", valueType = ValueType.STRING, defaultValue = "line1\nline2"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, """defaultValue = "line1\nline2"""")
        assertFalse(source.contains("line1\nline2"), "Raw newline must not appear in the generated source")
    }

    @Test
    fun `description with newline is escaped to backslash-n`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(
                            ":app",
                            flag(
                                key = "flag",
                                valueType = ValueType.BOOLEAN,
                                defaultValue = "false",
                                description = "first line\nsecond line",
                            ),
                        ),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, """description = "first line\nsecond line"""")
        assertFalse(source.contains("first line\nsecond line"), "Raw newline must not appear in description")
    }

    @Test
    fun `STRING default with tab is escaped to backslash-t`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests = listOf(manifest(":app", flag(key = "tabbed", valueType = ValueType.STRING, defaultValue = "col1\tcol2"))),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, """defaultValue = "col1\tcol2"""")
        assertFalse(source.contains("col1\tcol2"), "Raw tab must not appear in the generated source")
    }

    // NIT 5 — category emit/omit

    @Test
    fun `optional category is emitted when non-null`() {
        val source =
            GeneratedFeaturedRegistryGenerator.generate(
                manifests =
                    listOf(
                        manifest(
                            ":app",
                            flag(
                                key = "dark_mode",
                                valueType = ValueType.BOOLEAN,
                                defaultValue = "false",
                                category = "UI",
                            ),
                        ),
                    ),
                packageName = FEATURED_REGISTRY_PACKAGE,
            )
        assertContains(source, "category = \"UI\"")
    }
}
