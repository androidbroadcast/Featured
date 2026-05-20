package dev.androidbroadcast.featured.gradle.manifest

import dev.androidbroadcast.featured.gradle.RESOLVE_FLAGS_TASK_NAME
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateFeaturedManifestTaskRegistrationTest {
    @Test
    fun `plugin registers generateFeaturedManifest task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertTrue(
            project.tasks.names.contains(GENERATE_FEATURED_MANIFEST_TASK_NAME),
            "Expected '$GENERATE_FEATURED_MANIFEST_TASK_NAME' task to be registered by the plugin",
        )
    }

    @Test
    fun `generateFeaturedManifest task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FEATURED_MANIFEST_TASK_NAME)
        assertNotNull(task)
        assertTrue(
            task is GenerateFeaturedManifestTask,
            "Expected task type GenerateFeaturedManifestTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `generateFeaturedManifest task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FEATURED_MANIFEST_TASK_NAME)
        assertNotNull(task)
        assertEquals(
            "featured",
            task.group,
            "Expected task group 'featured' but was '${task.group}'",
        )
    }

    @Test
    fun `generateFeaturedManifest task output path follows convention`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FEATURED_MANIFEST_TASK_NAME) as? GenerateFeaturedManifestTask
        assertNotNull(task)
        val outputPath =
            task.outputFile
                .get()
                .asFile.path
        assertTrue(
            outputPath.endsWith("featured/featured-manifest.json"),
            "Expected outputFile path to end with 'featured/featured-manifest.json', got: $outputPath",
        )
    }

    @Test
    fun `generate fails with IllegalArgumentException when modulePath does not start with colon`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val emptyFlags = File.createTempFile("flags", ".txt").apply { deleteOnExit() }
        val task = project.tasks.findByName(GENERATE_FEATURED_MANIFEST_TASK_NAME) as GenerateFeaturedManifestTask
        task.modulePath.set("not-a-gradle-path")
        task.flagsFile.set(emptyFlags)

        val ex = assertFailsWith<IllegalArgumentException> { task.generate() }
        assertTrue(
            ex.message?.contains("not-a-gradle-path") == true,
            "Expected error message to name the offending path, got: ${ex.message}",
        )
        assertTrue(
            ex.message?.contains(":") == true,
            "Expected error message to mention the required ':' prefix, got: ${ex.message}",
        )
    }

    @Test
    fun `generateFeaturedManifest task depends on resolveFeatureFlags`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val manifestTask = project.tasks.findByName(GENERATE_FEATURED_MANIFEST_TASK_NAME)
        assertNotNull(manifestTask)
        val resolveTask = project.tasks.findByName(RESOLVE_FLAGS_TASK_NAME)
        assertNotNull(resolveTask)
        assertTrue(
            manifestTask.taskDependencies.getDependencies(manifestTask).contains(resolveTask),
            "Expected '$GENERATE_FEATURED_MANIFEST_TASK_NAME' to depend on '$RESOLVE_FLAGS_TASK_NAME'",
        )
    }
}
