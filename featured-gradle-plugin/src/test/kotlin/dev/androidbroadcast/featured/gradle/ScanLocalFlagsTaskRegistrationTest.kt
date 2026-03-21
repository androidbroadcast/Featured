package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScanLocalFlagsTaskRegistrationTest {
    @Test
    fun `plugin registers scanLocalFlags task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            project.tasks.findByName("scanLocalFlags"),
            "Expected 'scanLocalFlags' task to be registered by the plugin",
        )
    }

    @Test
    fun `scanLocalFlags task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName("scanLocalFlags")
        assertNotNull(task)
        assertTrue(
            task is ScanLocalFlagsTask,
            "Expected task type ScanLocalFlagsTask but was ${task::class.simpleName}",
        )
    }

    @Test
    fun `scanLocalFlags task has outputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName("scanLocalFlags") as? ScanLocalFlagsTask
        assertNotNull(task)
        assertTrue(
            task.outputFile.isPresent,
            "Expected outputFile to be configured on ScanLocalFlagsTask",
        )
    }

    @Test
    fun `scanLocalFlags task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName("scanLocalFlags")
        assertNotNull(task)
        assertTrue(
            task.group == "featured",
            "Expected task group 'featured' but was '${task.group}'",
        )
    }
}
