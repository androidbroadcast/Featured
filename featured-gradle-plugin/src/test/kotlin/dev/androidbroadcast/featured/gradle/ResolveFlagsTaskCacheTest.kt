package dev.androidbroadcast.featured.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
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

    @Test
    fun `resolveFeatureFlags is not served from cache when a flag default changes`() {
        val projectDir = tempFolder.newFolder("project")
        val cacheDir = tempFolder.newFolder("build-cache")

        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, flagDefault = false)

        // Run 1: cold cache → task executes and caches result.
        val run1 =
            gradleRunner(projectDir)
                .withArguments(RESOLVE_TASK, "--build-cache", "--stacktrace")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            run1.task(":$RESOLVE_TASK")?.outcome,
            "First run: expected SUCCESS.\n${run1.output}",
        )
        assertFlagsFileContains(projectDir, "my_flag|false|Boolean")

        // Change the flag default from false → true.
        writeBuildFile(projectDir, flagDefault = true)

        // Run 2: build-cache populated; the task MUST re-execute because its @Input changed.
        val run2 =
            gradleRunner(projectDir)
                .withArguments(RESOLVE_TASK, "--build-cache", "--stacktrace")
                .build()

        val outcome2 = run2.task(":$RESOLVE_TASK")?.outcome
        assertTrue(
            outcome2 == TaskOutcome.SUCCESS,
            "Second run: expected SUCCESS (task should re-run because flag default changed), " +
                "got $outcome2.\n${run2.output}",
        )
        assertFlagsFileContains(projectDir, "my_flag|true|Boolean")
    }

    @Test
    fun `resolveFeatureFlags is served from cache when nothing changes`() {
        val projectDir = tempFolder.newFolder("project")
        val cacheDir = tempFolder.newFolder("build-cache")

        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, flagDefault = false)

        gradleRunner(projectDir)
            .withArguments(RESOLVE_TASK, "--build-cache")
            .build()

        // Delete the output so the task must use the cache (simulates `clean`).
        projectDir.resolve("build").deleteRecursively()

        val run2 =
            gradleRunner(projectDir)
                .withArguments(RESOLVE_TASK, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.FROM_CACHE,
            run2.task(":$RESOLVE_TASK")?.outcome,
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

    private fun assertFlagsFileContains(projectDir: File, expected: String) {
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

    private companion object {
        const val RESOLVE_TASK = RESOLVE_FLAGS_TASK_NAME
    }
}
