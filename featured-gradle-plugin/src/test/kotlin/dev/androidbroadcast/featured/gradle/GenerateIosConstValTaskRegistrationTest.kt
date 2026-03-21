package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateIosConstValTaskRegistrationTest {
    @Test
    fun `plugin registers generateIosConstVal task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            project.tasks.findByName(GENERATE_IOS_CONST_VAL_TASK_NAME),
            "Expected '$GENERATE_IOS_CONST_VAL_TASK_NAME' task to be registered by the plugin",
        )
    }

    @Test
    fun `generateIosConstVal task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_IOS_CONST_VAL_TASK_NAME)
        assertNotNull(task)
        assertTrue(
            task is GenerateIosConstValTask,
            "Expected task type GenerateIosConstValTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `generateIosConstVal task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_IOS_CONST_VAL_TASK_NAME)
        assertNotNull(task)
        kotlin.test.assertEquals(
            "featured",
            task.group,
            "Expected task group 'featured' but was '${task.group}'",
        )
    }

    @Test
    fun `generateIosConstVal task has iosMainOutputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_IOS_CONST_VAL_TASK_NAME) as? GenerateIosConstValTask
        assertNotNull(task)
        assertTrue(
            task.iosMainOutputFile.isPresent,
            "Expected iosMainOutputFile to be configured on GenerateIosConstValTask",
        )
    }

    @Test
    fun `generateIosConstVal task has commonMainOutputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_IOS_CONST_VAL_TASK_NAME) as? GenerateIosConstValTask
        assertNotNull(task)
        assertTrue(
            task.commonMainOutputFile.isPresent,
            "Expected commonMainOutputFile to be configured on GenerateIosConstValTask",
        )
    }

    @Test
    fun `generateIosConstVal task depends on scanLocalFlags task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val generateTask = project.tasks.findByName(GENERATE_IOS_CONST_VAL_TASK_NAME)
        assertNotNull(generateTask)
        val scanTask = project.tasks.findByName(SCAN_TASK_NAME)
        assertNotNull(scanTask)
        assertTrue(
            generateTask.taskDependencies.getDependencies(generateTask).contains(scanTask),
            "Expected '$GENERATE_IOS_CONST_VAL_TASK_NAME' to depend on '$SCAN_TASK_NAME'",
        )
    }
}
