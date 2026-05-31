package dev.androidbroadcast.featured.gradle.manifest

import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("UnstableApiUsage")
class FeaturedManifestConfigurationTest {
    @Test
    fun `featuredManifest configuration is registered`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val cfg = project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)
        assertNotNull(cfg, "Expected '$FEATURED_MANIFEST_CONFIGURATION_NAME' configuration to be registered")
    }

    @Test
    fun `featuredManifest configuration has correct consumable flags`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val cfg = project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)
        assertNotNull(cfg)
        assertTrue(cfg.isCanBeConsumed, "Expected isCanBeConsumed = true")
        assertTrue(!cfg.isCanBeResolved, "Expected isCanBeResolved = false")
        assertTrue(!cfg.isCanBeDeclared, "Expected isCanBeDeclared = false")
    }

    @Test
    fun `featuredManifest configuration has usage attribute set to featured-manifest`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val cfg = project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)
        assertNotNull(cfg)
        val usageAttr = cfg.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
        assertNotNull(usageAttr, "Expected Usage attribute to be set")
        assertEquals(
            FEATURED_MANIFEST_USAGE,
            usageAttr.name,
            "Expected usage name '$FEATURED_MANIFEST_USAGE' but was '${usageAttr.name}'",
        )
    }

    @Test
    fun `featuredManifest configuration has schema-major attribute set to SCHEMA_VERSION`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val cfg = project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)
        assertNotNull(cfg)
        val schemaAttr = cfg.attributes.getAttribute(schemaMajorAttr)
        assertNotNull(schemaAttr, "Expected schema-major attribute to be set")
        assertEquals(
            SCHEMA_VERSION,
            schemaAttr,
            "Expected schema-major attribute = $SCHEMA_VERSION but was $schemaAttr",
        )
    }

    @Test
    fun `featuredManifest configuration has outgoing artifacts`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val cfg = project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)
        assertNotNull(cfg)
        assertTrue(
            cfg.outgoing.artifacts.isNotEmpty(),
            "Expected at least one outgoing artifact on '$FEATURED_MANIFEST_CONFIGURATION_NAME'",
        )
    }

    @Test
    fun `featuredManifest artifact is built by generateFeaturedManifest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val cfg = project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)
        assertNotNull(cfg)
        val deps =
            cfg.outgoing.artifacts.buildDependencies
                .getDependencies(null)
        val taskNames = deps.map { it.name }
        assertTrue(
            taskNames.contains(GENERATE_FEATURED_MANIFEST_TASK_NAME),
            "Expected artifact built by '$GENERATE_FEATURED_MANIFEST_TASK_NAME', got: $taskNames",
        )
    }

    @Test
    fun `accessing featuredManifest configuration does not eagerly realize generateFeaturedManifest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        // Accessing the configuration by name must not trigger task realization.
        project.configurations.findByName(FEATURED_MANIFEST_CONFIGURATION_NAME)

        // The task must still be present in the task graph (registered lazily).
        assertTrue(
            project.tasks.names.contains(GENERATE_FEATURED_MANIFEST_TASK_NAME),
            "Expected '$GENERATE_FEATURED_MANIFEST_TASK_NAME' to be in task names (lazy)",
        )
    }
}
