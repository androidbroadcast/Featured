package dev.androidbroadcast.featured.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateProguardRulesTaskRegistrationTest {
    @Test
    fun `plugin registers generateProguardRules task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        assertNotNull(
            project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME),
            "Expected '$GENERATE_PROGUARD_TASK_NAME' task to be registered",
        )
    }

    @Test
    fun `generateProguardRules task is of correct type`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME)
        assertNotNull(task)
        assertTrue(task is GenerateProguardRulesTask, "Expected GenerateProguardRulesTask, was ${task::class.simpleName}")
    }

    @Test
    fun `generateProguardRules task is in featured group`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME)
        assertNotNull(task)
        assertEquals("featured", task.group)
    }

    @Test
    fun `generateProguardRules task has outputFile configured`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val task = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME) as? GenerateProguardRulesTask
        assertNotNull(task)
        assertTrue(task.outputFile.isPresent, "Expected outputFile to be configured")
    }

    @Test
    fun `generateProguardRules task depends on resolveFeatureFlags task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("dev.androidbroadcast.featured")

        val generateTask = project.tasks.findByName(GENERATE_PROGUARD_TASK_NAME)
        val resolveTask = project.tasks.findByName(RESOLVE_FLAGS_TASK_NAME)
        assertNotNull(generateTask)
        assertNotNull(resolveTask)
        assertTrue(
            generateTask.taskDependencies.getDependencies(generateTask).contains(resolveTask),
            "Expected '$GENERATE_PROGUARD_TASK_NAME' to depend on '$RESOLVE_FLAGS_TASK_NAME'",
        )
    }
}
