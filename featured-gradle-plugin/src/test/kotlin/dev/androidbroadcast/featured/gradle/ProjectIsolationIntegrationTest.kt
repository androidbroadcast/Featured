package dev.androidbroadcast.featured.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the Featured Gradle plugin is compatible with Gradle Project Isolation.
 *
 * The plugin formerly registered a `scanAllLocalFlags` task on the root project from
 * within a sub-project's configuration — a canonical Project Isolation violation. That
 * access has been removed (#186). This test is the empirical proof: a multi-module build
 * with isolated-projects enabled must configure and run `resolveFeatureFlags` without any
 * cross-project access error.
 *
 * Project Isolation is enabled via `org.gradle.unsafe.isolated-projects=true`. The `unsafe.`
 * prefix is still required as of Gradle 9.4.1 (verified against the official Gradle 9.4.1
 * user guide, "How do I use it?" section).
 *
 * No Android SDK is required; the fixture uses plain `java-library` modules.
 */
class ProjectIsolationIntegrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("pi-test-project")

        // Root settings: include the feature module.
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "pi-test-project"
            include(":feature-module")
            """.trimIndent(),
        )

        // Root gradle.properties: enable Project Isolation.
        // The unsafe. prefix is still required as of Gradle 9.4.1.
        projectDir.resolve("gradle.properties").writeText(
            "org.gradle.unsafe.isolated-projects=true\n",
        )

        // Minimal root build script (no plugin applied at root).
        projectDir.resolve("build.gradle.kts").writeText(
            "// root build — no configuration here\n",
        )

        // Feature sub-module: applies the Featured plugin with two local flags.
        val featureDir = projectDir.resolve("feature-module").also { it.mkdirs() }
        featureDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    boolean("dark_mode", default = false)
                    boolean("promo_banner", default = true)
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `resolveFeatureFlags succeeds with isolated-projects enabled`() {
        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .forwardOutput()
                // Run a lightweight task that exercises plugin configuration without a long build.
                // --configuration-cache is passed explicitly so the CC engagement marker
                // ("Configuration cache entry stored") is deterministically present on a cold run,
                // letting us assert that Project Isolation (which implies CC) actually engaged.
                .withArguments(
                    ":feature-module:$RESOLVE_FLAGS_TASK_NAME",
                    "--configuration-cache",
                    "--stacktrace",
                ).build()

        // Structured outcome check: subsumes both "task ran" and "build succeeded" in one assertion.
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":feature-module:$RESOLVE_FLAGS_TASK_NAME")?.outcome,
            "Expected :feature-module:$RESOLVE_FLAGS_TASK_NAME to complete with SUCCESS.\n${result.output}",
        )

        // Assert no Project Isolation violation in the build output.
        // A violation from rootProject access would contain this substring.
        // This is the authoritative guard: Gradle always uses this exact phrase in PI violation
        // messages, and it never appears in the benign incubating-feature banner.
        assertFalse(
            result.output.contains("cannot access project"),
            "Build output contains a Project Isolation violation — rootProject access detected.\n${result.output}",
        )
        // Note: a generic "isolated projects" substring check (even case-insensitively) is NOT
        // usable here — Gradle prints "Isolated Projects is an incubating feature." on EVERY run
        // when the feature is enabled, which would cause a false failure. The "cannot access
        // project" assertion above is sufficient as the authoritative PI-violation guard.

        // Assert that Project Isolation / Configuration Cache actually engaged.
        // With --configuration-cache on a cold run, Gradle 9.x prints
        // "Configuration cache entry stored." confirming the CC (and therefore PI) was active.
        // Without this check the test would pass vacuously if the property name were silently
        // ignored (Gradle ignores unknown properties).
        assertTrue(
            result.output.contains("Configuration cache entry stored"),
            "Expected Configuration Cache engagement marker in output — " +
                "Project Isolation may not have been active (check property name).\n${result.output}",
        )
    }
}
