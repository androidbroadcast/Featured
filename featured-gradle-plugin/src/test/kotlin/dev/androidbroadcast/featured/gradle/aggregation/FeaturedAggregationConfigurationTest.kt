package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.schemaMajorAttr
import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("UnstableApiUsage")
class FeaturedAggregationConfigurationTest {
    @Test
    fun `featuredAggregation configuration is registered`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val cfg = project.configurations.findByName(FEATURED_AGGREGATION_CONFIGURATION_NAME)
        assertNotNull(cfg, "Expected '$FEATURED_AGGREGATION_CONFIGURATION_NAME' configuration to be registered")
    }

    @Test
    fun `featuredAggregation is declarable not consumable not resolvable`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val cfg = project.configurations.findByName(FEATURED_AGGREGATION_CONFIGURATION_NAME)
        assertNotNull(cfg)
        assertTrue(cfg.isCanBeDeclared, "Expected isCanBeDeclared = true")
        assertTrue(!cfg.isCanBeConsumed, "Expected isCanBeConsumed = false")
        assertTrue(!cfg.isCanBeResolved, "Expected isCanBeResolved = false")
    }

    @Test
    fun `featuredAggregationClasspath configuration is registered`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val cfg = project.configurations.findByName(FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME)
        assertNotNull(cfg, "Expected '$FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME' configuration to be registered")
    }

    @Test
    fun `featuredAggregationClasspath is resolvable not consumable not declarable`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val cfg = project.configurations.findByName(FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME)
        assertNotNull(cfg)
        assertTrue(cfg.isCanBeResolved, "Expected isCanBeResolved = true")
        assertTrue(!cfg.isCanBeConsumed, "Expected isCanBeConsumed = false")
        assertTrue(!cfg.isCanBeDeclared, "Expected isCanBeDeclared = false")
    }

    @Test
    fun `featuredAggregationClasspath has Usage attribute featured-manifest`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val cfg = project.configurations.findByName(FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME)
        assertNotNull(cfg)
        val usageAttr = cfg.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
        assertNotNull(usageAttr, "Expected Usage attribute to be set")
        assertEquals(
            "featured-manifest",
            usageAttr.name,
            "Expected Usage attribute name 'featured-manifest', got '${usageAttr.name}'",
        )
    }

    @Test
    fun `featuredAggregationClasspath has schema-major attribute equal to SCHEMA_VERSION`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val cfg = project.configurations.findByName(FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME)
        assertNotNull(cfg)
        val schemaAttr = cfg.attributes.getAttribute(schemaMajorAttr)
        assertNotNull(schemaAttr, "Expected schema-major attribute to be set")
        assertEquals(
            SCHEMA_VERSION,
            schemaAttr,
            "Expected schema-major = $SCHEMA_VERSION, got $schemaAttr",
        )
    }

    @Test
    fun `featuredAggregationClasspath extends featuredAggregation`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val declarable = project.configurations.findByName(FEATURED_AGGREGATION_CONFIGURATION_NAME)
        val classpath = project.configurations.findByName(FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME)
        assertNotNull(declarable)
        assertNotNull(classpath)
        assertTrue(
            classpath.extendsFrom.contains(declarable),
            "Expected featuredAggregationClasspath to extend featuredAggregation",
        )
    }
}
