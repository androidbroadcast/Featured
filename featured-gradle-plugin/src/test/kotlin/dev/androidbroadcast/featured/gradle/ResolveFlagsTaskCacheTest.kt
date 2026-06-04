package dev.androidbroadcast.featured.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for issue #238: changing a flag's `default` value must invalidate the
 * build cache for `resolveFeatureFlags` (and, transitively, all downstream generators).
 *
 * The test uses a minimal JVM fixture (no AGP / `ANDROID_HOME` required) and a local
 * build-cache directory scoped to the `TemporaryFolder` so the cache lifecycle is fully
 * controlled.
 */
class ResolveFlagsTaskCacheTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("project")
        cacheDir = tempFolder.newFolder("build-cache")
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, flagDefault = false)
    }

    @Test
    fun `resolveFeatureFlags is not served from cache when a flag default changes`() {
        // Run 1: cold cache → task executes and caches result.
        val run1 =
            gradleRunner(projectDir)
                .withArguments(RESOLVE_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            run1.task(":$RESOLVE_FLAGS_TASK_NAME")?.outcome,
            "First run: expected SUCCESS.\n${run1.output}",
        )
        assertFlagsFileContains(projectDir, "my_flag|false|Boolean")

        // Change the flag default from false → true.
        writeBuildFile(projectDir, flagDefault = true)

        // Run 2: build-cache populated; the task MUST re-execute because its @Input changed.
        val run2 =
            gradleRunner(projectDir)
                .withArguments(RESOLVE_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            run2.task(":$RESOLVE_FLAGS_TASK_NAME")?.outcome,
            "Second run: expected SUCCESS (task should re-run because flag default changed).\n${run2.output}",
        )
        assertFlagsFileContains(projectDir, "my_flag|true|Boolean")
    }

    @Test
    fun `resolveFeatureFlags is served from cache when nothing changes`() {
        gradleRunner(projectDir)
            .withArguments(RESOLVE_FLAGS_TASK_NAME, "--build-cache")
            .build()

        // Delete the output so the task must use the cache (simulates `clean`).
        projectDir.resolve("build").deleteRecursively()

        val run2 =
            gradleRunner(projectDir)
                .withArguments(RESOLVE_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.FROM_CACHE,
            run2.task(":$RESOLVE_FLAGS_TASK_NAME")?.outcome,
            "Second run with unchanged inputs should be FROM_CACHE.\n${run2.output}",
        )
        assertFlagsFileContains(projectDir, "my_flag|false|Boolean")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun writeSettingsFile(projectDir: File, cacheDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "cache-test"
            buildCache {
                local {
                    directory = "${cacheDir.absolutePath.replace("\\", "/")}"
                    isEnabled = true
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeBuildFile(projectDir: File, flagDefault: Boolean) {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    boolean("my_flag", default = $flagDefault)
                }
            }
            """.trimIndent(),
        )
    }

    private fun assertFlagsFileContains(
        projectDir: File,
        expected: String,
    ) {
        val flagsFile = projectDir.resolve("build/featured/flags.txt")
        assertTrue(flagsFile.exists(), "flags.txt must exist at ${flagsFile.path}")
        val content = flagsFile.readText()
        assertTrue(
            content.contains(expected),
            "Expected flags.txt to contain '$expected'.\nActual:\n$content",
        )
    }

    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
