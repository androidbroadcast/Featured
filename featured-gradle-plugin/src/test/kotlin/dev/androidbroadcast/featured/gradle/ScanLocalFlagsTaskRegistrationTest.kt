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
            project.tasks.findByName(SCAN_TASK_NAME),
            "Expected '$SCAN_TASK_NAME' task to be registered by the plugin",
        )
    }

    @Test
    fun `scanLocalFlags task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(SCAN_TASK_NAME)
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

        val task = project.tasks.findByName(SCAN_TASK_NAME) as? ScanLocalFlagsTask
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

        val task = project.tasks.findByName(SCAN_TASK_NAME)
        assertNotNull(task)
        assertTrue(
            task.group == "featured",
            "Expected task group 'featured' but was '${task.group}'",
        )
    }

    @Test
    fun `plugin registers scanAllLocalFlags aggregation task on root project`() {
        val root = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().withParent(root).build()
        child.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            root.tasks.findByName(SCAN_ALL_TASK_NAME),
            "Expected '$SCAN_ALL_TASK_NAME' aggregation task on root project",
        )
    }

    @Test
    fun `scanAllLocalFlags task depends on module scanLocalFlags task`() {
        val root = ProjectBuilder.builder().build()
        val child = ProjectBuilder.builder().withParent(root).build()
        child.plugins.apply("dev.androidbroadcast.featured")

        val aggregator = root.tasks.findByName(SCAN_ALL_TASK_NAME)
        assertNotNull(aggregator)
        val childScanTask = child.tasks.findByName(SCAN_TASK_NAME)
        assertNotNull(childScanTask)
        assertTrue(
            aggregator.taskDependencies.getDependencies(aggregator).contains(childScanTask),
            "Expected '$SCAN_ALL_TASK_NAME' to depend on child '$SCAN_TASK_NAME'",
        )
    }

    @Test
    fun `applying plugin to multiple modules wires all to scanAllLocalFlags`() {
        val root = ProjectBuilder.builder().build()
        val moduleA =
            ProjectBuilder
                .builder()
                .withParent(root)
                .withName("moduleA")
                .build()
        val moduleB =
            ProjectBuilder
                .builder()
                .withParent(root)
                .withName("moduleB")
                .build()
        moduleA.plugins.apply("dev.androidbroadcast.featured")
        moduleB.plugins.apply("dev.androidbroadcast.featured")

        val aggregator = root.tasks.findByName(SCAN_ALL_TASK_NAME)
        assertNotNull(aggregator)
        val deps = aggregator.taskDependencies.getDependencies(aggregator)
        val depNames = deps.map { it.name }
        assertTrue(
            depNames.contains(SCAN_TASK_NAME),
            "Expected aggregator to depend on scan tasks from both modules, got: $depNames",
        )
        assertTrue(deps.size >= 2, "Expected at least 2 module scan tasks, got ${deps.size}")
    }
}
