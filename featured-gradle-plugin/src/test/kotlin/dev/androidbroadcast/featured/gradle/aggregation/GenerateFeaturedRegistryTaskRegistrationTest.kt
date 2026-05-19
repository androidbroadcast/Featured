package dev.androidbroadcast.featured.gradle.aggregation

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("UnstableApiUsage")
class GenerateFeaturedRegistryTaskRegistrationTest {
    @Test
    fun `plugin registers generateFeaturedRegistry task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        assertTrue(
            project.tasks.names.contains(GENERATE_FEATURED_REGISTRY_TASK_NAME),
            "Expected '$GENERATE_FEATURED_REGISTRY_TASK_NAME' task to be registered lazily by the plugin",
        )
    }

    @Test
    fun `generateFeaturedRegistry task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val task = project.tasks.findByName(GENERATE_FEATURED_REGISTRY_TASK_NAME)
        assertNotNull(task, "Expected '$GENERATE_FEATURED_REGISTRY_TASK_NAME' task to be registered")
        assertTrue(
            task is GenerateFeaturedRegistryTask,
            "Expected task type GenerateFeaturedRegistryTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `generateFeaturedRegistry task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val task = project.tasks.findByName(GENERATE_FEATURED_REGISTRY_TASK_NAME) as? GenerateFeaturedRegistryTask
        assertNotNull(task)
        assertEquals("featured", task.group, "Expected task group 'featured' but was '${task.group}'")
    }

    @Test
    fun `generateFeaturedRegistry task outputPackage defaults to FEATURED_REGISTRY_PACKAGE`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val task = project.tasks.findByName(GENERATE_FEATURED_REGISTRY_TASK_NAME) as? GenerateFeaturedRegistryTask
        assertNotNull(task)
        assertEquals(
            FEATURED_REGISTRY_PACKAGE,
            task.outputPackage.get(),
            "Expected outputPackage == FEATURED_REGISTRY_PACKAGE",
        )
    }

    @Test
    fun `generateFeaturedRegistry task outputFile path follows convention`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        val task = project.tasks.findByName(GENERATE_FEATURED_REGISTRY_TASK_NAME) as? GenerateFeaturedRegistryTask
        assertNotNull(task)
        val outputPath =
            task.outputFile
                .get()
                .asFile.path
        assertTrue(
            outputPath.endsWith("build/generated/featured/commonMain/${FEATURED_REGISTRY_OBJECT}.kt"),
            "Expected outputFile to end with 'build/generated/featured/commonMain/${FEATURED_REGISTRY_OBJECT}.kt', got: $outputPath",
        )
    }

    @Test
    fun `accessing featuredAggregationClasspath configuration does not eagerly realize generateFeaturedRegistry task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured.application")

        // Accessing the configuration by name must not trigger task realization.
        project.configurations.getByName(FEATURED_AGGREGATION_CLASSPATH_CONFIGURATION_NAME)

        // The task must still be present in the task graph (registered lazily).
        assertTrue(
            project.tasks.names.contains(GENERATE_FEATURED_REGISTRY_TASK_NAME),
            "Expected '$GENERATE_FEATURED_REGISTRY_TASK_NAME' to be in task names (lazy)",
        )
    }
}
