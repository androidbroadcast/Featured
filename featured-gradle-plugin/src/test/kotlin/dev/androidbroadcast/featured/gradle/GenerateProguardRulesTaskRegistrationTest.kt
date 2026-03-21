package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateProguardRulesTaskRegistrationTest {
    @Test
    fun `plugin registers generateProguardRules task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME),
            "Expected '$GENERATE_PROGUARD_TASK_NAME' task to be registered by the plugin",
        )
    }

    @Test
    fun `generateProguardRules task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME)
        assertNotNull(task)
        assertTrue(
            task is GenerateProguardRulesTask,
            "Expected task type GenerateProguardRulesTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `generateProguardRules task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME)
        assertNotNull(task)
        assertEquals(
            "featured",
            task.group,
            "Expected task group 'featured' but was '${task.group}'",
        )
    }

    @Test
    fun `generateProguardRules task has outputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME) as? GenerateProguardRulesTask
        assertNotNull(task)
        assertTrue(
            task.outputFile.isPresent,
            "Expected outputFile to be configured on GenerateProguardRulesTask",
        )
    }

    @Test
    fun `generateProguardRules task depends on scanLocalFlags task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val generateTask = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME)
        assertNotNull(generateTask)
        val scanTask = project.tasks.findByName(SCAN_TASK_NAME)
        assertNotNull(scanTask)
        assertTrue(
            generateTask.taskDependencies.getDependencies(generateTask).contains(scanTask),
            "Expected '$GENERATE_PROGUARD_TASK_NAME' to depend on '$SCAN_TASK_NAME'",
        )
    }
}

// Local helper for cleaner assertions
private fun assertEquals(expected: String, actual: String?, message: String) {
    kotlin.test.assertEquals(expected, actual, message)
}
