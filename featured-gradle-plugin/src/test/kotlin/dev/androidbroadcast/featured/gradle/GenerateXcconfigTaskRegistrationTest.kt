package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateXcconfigTaskRegistrationTest {

    @Test
    fun `plugin registers generateXcconfig task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            project.tasks.findByName(GENERATE_XCCONFIG_TASK_NAME),
            "Expected '$GENERATE_XCCONFIG_TASK_NAME' task to be registered by the plugin",
        )
    }

    @Test
    fun `generateXcconfig task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_XCCONFIG_TASK_NAME)
        assertNotNull(task)
        assertTrue(
            task is GenerateXcconfigTask,
            "Expected task type GenerateXcconfigTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `generateXcconfig task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_XCCONFIG_TASK_NAME)
        assertNotNull(task)
        kotlin.test.assertEquals(
            "featured",
            task.group,
            "Expected task group 'featured' but was '${task.group}'",
        )
    }

    @Test
    fun `generateXcconfig task has outputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_XCCONFIG_TASK_NAME) as? GenerateXcconfigTask
        assertNotNull(task)
        assertTrue(
            task.outputFile.isPresent,
            "Expected outputFile to be configured on GenerateXcconfigTask",
        )
    }

    @Test
    fun `generateXcconfig task depends on scanLocalFlags task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val generateTask = project.tasks.findByName(GENERATE_XCCONFIG_TASK_NAME)
        assertNotNull(generateTask)
        val scanTask = project.tasks.findByName(SCAN_TASK_NAME)
        assertNotNull(scanTask)
        assertTrue(
            generateTask.taskDependencies.getDependencies(generateTask).contains(scanTask),
            "Expected '$GENERATE_XCCONFIG_TASK_NAME' to depend on '$SCAN_TASK_NAME'",
        )
    }

    @Test
    fun `generateXcconfig output file path contains FeatureFlags generated xcconfig`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_XCCONFIG_TASK_NAME) as? GenerateXcconfigTask
        assertNotNull(task)
        assertTrue(
            task.outputFile.get().asFile.name == "FeatureFlags.generated.xcconfig",
            "Expected output file name 'FeatureFlags.generated.xcconfig' but was '${task.outputFile.get().asFile.name}'",
        )
    }
}
