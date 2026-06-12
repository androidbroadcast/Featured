package dev.androidbroadcast.featured.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the `generation { }` DSL settings flow from [FeaturedExtension] into the
 * codegen tasks with the documented fallback chain (section → module-wide → built-in
 * default), and that invalid values fail with messages naming the exact DSL property.
 */
class FeaturedExtensionGenerationSettingsTest {
    private fun projectWithPlugin(): Project =
        ProjectBuilder
            .builder()
            .build()
            .also { it.plugins.apply("dev.androidbroadcast.featured") }

    private val Project.featured: FeaturedExtension
        get() = extensions.getByType(FeaturedExtension::class.java)

    private val Project.configParamTask: GenerateConfigParamTask
        get() = tasks.getByName(GENERATE_CONFIG_PARAM_TASK_NAME) as GenerateConfigParamTask

    private val Project.proguardTask: GenerateProguardRulesTask
        get() = tasks.getByName(GENERATE_PROGUARD_TASK_NAME) as GenerateProguardRulesTask

    private val Project.iosConstValTask: GenerateIosConstValTask
        get() = tasks.getByName(GENERATE_IOS_CONST_VAL_TASK_NAME) as GenerateIosConstValTask

    // ── defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `built-in defaults apply when nothing is configured`() {
        val project = projectWithPlugin()
        val task = project.configParamTask
        assertEquals(ConfigParamGenerator.DEFAULT_PACKAGE, task.localPackageName.get())
        assertEquals(ConfigParamGenerator.DEFAULT_PACKAGE, task.remotePackageName.get())
        assertFalse(task.localClassName.isPresent, "No custom local class name expected by default")
        assertFalse(task.remoteClassName.isPresent, "No custom remote class name expected by default")
        assertEquals(FeaturedVisibility.INTERNAL, task.localVisibility.get())
        assertEquals(FeaturedVisibility.INTERNAL, task.remoteVisibility.get())
        assertEquals(ConfigParamGenerator.DEFAULT_PACKAGE, project.proguardTask.packageName.get())
        assertEquals(ConfigParamGenerator.DEFAULT_PACKAGE, project.iosConstValTask.packageName.get())
    }

    // ── module-wide defaults ──────────────────────────────────────────────────

    @Test
    fun `top-level generation block applies to both sections`() {
        val project = projectWithPlugin()
        project.featured.generation {
            packageName = "com.example.flags"
            visibility = FeaturedVisibility.PUBLIC
        }
        val task = project.configParamTask
        assertEquals("com.example.flags", task.localPackageName.get())
        assertEquals("com.example.flags", task.remotePackageName.get())
        assertEquals(FeaturedVisibility.PUBLIC, task.localVisibility.get())
        assertEquals(FeaturedVisibility.PUBLIC, task.remoteVisibility.get())
        assertEquals("com.example.flags", project.proguardTask.packageName.get())
        assertEquals("com.example.flags", project.iosConstValTask.packageName.get())
    }

    // ── per-section overrides ─────────────────────────────────────────────────

    @Test
    fun `section generation block overrides the top-level default`() {
        val project = projectWithPlugin()
        project.featured.apply {
            generation {
                packageName = "com.example.flags"
            }
            localFlags {
                generation {
                    className = "CheckoutLocalFlags"
                    packageName = "com.example.local"
                    visibility = FeaturedVisibility.PUBLIC
                }
            }
        }
        val task = project.configParamTask
        assertEquals("com.example.local", task.localPackageName.get())
        assertEquals("CheckoutLocalFlags", task.localClassName.get())
        assertEquals(FeaturedVisibility.PUBLIC, task.localVisibility.get())
        // Remote section falls back to the top-level default.
        assertEquals("com.example.flags", task.remotePackageName.get())
        assertFalse(task.remoteClassName.isPresent)
        assertEquals(FeaturedVisibility.INTERNAL, task.remoteVisibility.get())
        // ProGuard rules and iOS const vals target the local section's package.
        assertEquals("com.example.local", project.proguardTask.packageName.get())
        assertEquals("com.example.local", project.iosConstValTask.packageName.get())
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    fun `invalid section package name fails naming the section property`() {
        val project = projectWithPlugin()
        project.featured.localFlags { generation { packageName = "1bad.package" } }
        assertFailureMessageContains("featured.localFlags.generation.packageName '1bad.package' is not a valid Kotlin package name.") {
            project.configParamTask.localPackageName.get()
        }
    }

    @Test
    fun `invalid top-level package name fails naming the top-level property`() {
        val project = projectWithPlugin()
        project.featured.generation { packageName = "com..broken" }
        assertFailureMessageContains("featured.generation.packageName 'com..broken' is not a valid Kotlin package name.") {
            project.configParamTask.remotePackageName.get()
        }
    }

    @Test
    fun `invalid class name fails naming the section property`() {
        val project = projectWithPlugin()
        project.featured.remoteFlags { generation { className = "My Flags" } }
        assertFailureMessageContains("featured.remoteFlags.generation.className 'My Flags' is not a valid Kotlin class name.") {
            project.configParamTask.remoteClassName.get()
        }
    }

    @Test
    fun `top-level className is rejected`() {
        val project = projectWithPlugin()
        project.featured.generation { className = "SharedFlags" }
        assertFailureMessageContains("featured.generation.className is not supported") {
            project.configParamTask.localClassName.get()
        }
    }

    /**
     * Querying a failing provider through a Gradle `Property` wraps the underlying
     * [IllegalArgumentException] in a `PropertyQueryException`, so the expected message is
     * asserted across the cause chain.
     */
    private fun assertFailureMessageContains(
        expected: String,
        block: () -> Unit,
    ) {
        val failure = assertFailsWith<RuntimeException> { block() }
        val messages = generateSequence(failure as Throwable) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(
            messages.any { expected in it },
            "Expected a failure message containing '$expected', got: $messages",
        )
    }

    @Test
    fun `identical effective local and remote FQNs are rejected`() {
        val local = ObjectCodegen(packageName = "com.example", objectName = "Flags")
        val remote = ObjectCodegen(packageName = "com.example", objectName = "Flags")
        val failure = assertFailsWith<IllegalArgumentException> { validateDistinctObjectFqns(":app", local, remote) }
        assertContains(failure.message.orEmpty(), "both resolve to 'com.example.Flags'")
    }

    @Test
    fun `same class name in different packages is allowed`() {
        val local = ObjectCodegen(packageName = "com.example.local", objectName = "Flags")
        val remote = ObjectCodegen(packageName = "com.example.remote", objectName = "Flags")
        // Must not throw — the FQNs differ.
        validateDistinctObjectFqns(":app", local, remote)
    }
}
