package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateFlagRegistrarTaskRegistrationTest {
    @Test
    fun `plugin registers generateFlagRegistrar task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME),
            "Expected '$GENERATE_FLAG_REGISTRAR_TASK_NAME' task to be registered by the plugin",
        )
    }

    @Test
    fun `generateFlagRegistrar task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME)
        assertNotNull(task)
        assertTrue(
            task is GenerateFlagRegistrarTask,
            "Expected task type GenerateFlagRegistrarTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `generateFlagRegistrar task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME)
        assertNotNull(task)
        assertEquals(
            "featured",
            task.group,
            "Expected task group 'featured' but was '${task.group}'",
        )
    }

    @Test
    fun `generateFlagRegistrar task has outputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME) as? GenerateFlagRegistrarTask
        assertNotNull(task)
        assertTrue(
            task.outputFile.isPresent,
            "Expected outputFile to be configured on GenerateFlagRegistrarTask",
        )
    }

    @Test
    fun `generateFlagRegistrar task has packageName configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME) as? GenerateFlagRegistrarTask
        assertNotNull(task)
        assertTrue(
            task.packageName.isPresent,
            "Expected packageName to be configured on GenerateFlagRegistrarTask",
        )
        assertEquals(
            "dev.androidbroadcast.featured.generated",
            task.packageName.get(),
            "Expected default package name 'dev.androidbroadcast.featured.generated'",
        )
    }

    @Test
    fun `generateFlagRegistrar task depends on resolveFeatureFlags task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val generateTask = project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME)
        assertNotNull(generateTask)
        val scanTask = project.tasks.findByName(RESOLVE_FLAGS_TASK_NAME)
        assertNotNull(scanTask)
        assertTrue(
            generateTask.taskDependencies.getDependencies(generateTask).contains(scanTask),
            "Expected '$GENERATE_FLAG_REGISTRAR_TASK_NAME' to depend on '$RESOLVE_FLAGS_TASK_NAME'",
        )
    }

    @Test
    fun `generateFlagRegistrar outputFile is inside build generated featured directory`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_FLAG_REGISTRAR_TASK_NAME) as? GenerateFlagRegistrarTask
        assertNotNull(task)
        val outputPath =
            task.outputFile
                .get()
                .asFile.path
        assertTrue(
            outputPath.contains("generated/featured"),
            "Expected outputFile inside 'generated/featured' directory, got: $outputPath",
        )
    }
}
