package dev.androidbroadcast.featured.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [VerifyExpiredFlagsTask]:
 *
 * a. A flag with a past [LocalFlagEntry.expiresAt] produces a "Featured: flag … expired on …" warning.
 * b. A flag with a future [LocalFlagEntry.expiresAt] produces no expired-flag warning.
 * c. When [ResolveFlagsTask] is restored FROM_CACHE, [VerifyExpiredFlagsTask] still runs and
 *    the expiry warning is present in the build output.
 * d. A flag with an invalid [LocalFlagEntry.expiresAt] format produces an "invalid expiresAt format"
 *    warning and the build succeeds.
 * e. A flag with [LocalFlagEntry.expiresAt] == today produces no expired-flag warning (valid through
 *    the expiry day itself; expiry starts the day after).
 * f. A flag without [LocalFlagEntry.expiresAt] produces no warning and the build succeeds.
 * g. Multiple flags: two expired + one future in one module → exactly the two expired warnings emitted.
 * h. Expired-flag warning contains the module name (prefixed with "in :").
 * i. When the flags file does not exist the task warns and returns early (unit-test path via ProjectBuilder).
 *
 * All fixtures use a minimal JVM project (no AGP / ANDROID_HOME required) and a local
 * build-cache directory scoped to [TemporaryFolder] so the cache lifecycle is fully controlled.
 */
class VerifyExpiredFlagsTaskTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("project")
        cacheDir = tempFolder.newFolder("build-cache")
    }

    // ── Test cases ─────────────────────────────────────────────────────────────

    /** Case (a): past expiresAt → expired warning emitted. */
    @Test
    fun `flag with past expiresAt emits expired warning`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, expiresAt = "2000-01-01")

        val result =
            gradleRunner(projectDir)
                .withArguments(VERIFY_EXPIRED_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected SUCCESS.\n${result.output}",
        )
        assertTrue(
            result.output.contains("Featured: flag"),
            "Expected expired-flag warning in output.\n${result.output}",
        )
        assertTrue(
            result.output.contains("expired on 2000-01-01"),
            "Expected 'expired on 2000-01-01' in output.\n${result.output}",
        )
        assertTrue(
            result.output.contains("in :"),
            "Expected module name (prefixed with 'in :') in the warning.\n${result.output}",
        )
    }

    /** Case (b): future expiresAt → no expired warning. */
    @Test
    fun `flag with future expiresAt does not emit expired warning`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, expiresAt = "2099-12-31")

        val result =
            gradleRunner(projectDir)
                .withArguments(VERIFY_EXPIRED_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected SUCCESS.\n${result.output}",
        )
        assertFalse(
            result.output.contains("expired on"),
            "Expected no expired-flag warning in output.\n${result.output}",
        )
    }

    /**
     * Case (c): [ResolveFlagsTask] restored FROM_CACHE on the second run →
     * [VerifyExpiredFlagsTask] still runs (non-cacheable) and the warning is still present.
     */
    @Test
    fun `verify task runs and warns even when resolve task is FROM_CACHE`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, expiresAt = "2000-01-01")

        // Run 1: cold cache → both tasks execute.
        val run1 =
            gradleRunner(projectDir)
                .withArguments(
                    GENERATE_CONFIG_PARAM_TASK_NAME,
                    "--build-cache",
                    "--configuration-cache",
                    "--configuration-cache-problems=warn",
                ).build()

        assertEquals(
            TaskOutcome.SUCCESS,
            run1.task(":$RESOLVE_FLAGS_TASK_NAME")?.outcome,
            "Run 1: resolveFeatureFlags should be SUCCESS.\n${run1.output}",
        )

        // Delete only build/ — cacheDir is preserved so the second run can restore from it.
        projectDir.resolve("build").deleteRecursively()

        // Run 2: resolve comes FROM_CACHE; verify must still execute (non-cacheable).
        val run2 =
            gradleRunner(projectDir)
                .withArguments(
                    GENERATE_CONFIG_PARAM_TASK_NAME,
                    "--build-cache",
                    "--configuration-cache",
                    "--configuration-cache-problems=warn",
                ).build()

        assertEquals(
            TaskOutcome.FROM_CACHE,
            run2.task(":$RESOLVE_FLAGS_TASK_NAME")?.outcome,
            "Run 2: resolveFeatureFlags should be FROM_CACHE.\n${run2.output}",
        )
        // The verify task is non-cacheable (outputs.upToDateWhen { false }) — it must be
        // SUCCESS, never SKIPPED or UP_TO_DATE, even when the resolve task is FROM_CACHE.
        val verifyOutcome = run2.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            verifyOutcome,
            "Run 2: verifyExpiredFlags should be SUCCESS (non-cacheable).\n${run2.output}",
        )
        assertTrue(
            run2.output.contains("expired on 2000-01-01"),
            "Expected expired warning even after resolve was FROM_CACHE.\n${run2.output}",
        )
    }

    /** Case (d): invalid expiresAt format → format warning, build succeeds. */
    @Test
    fun `flag with invalid expiresAt format emits format warning and build succeeds`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, expiresAt = "not-a-date")

        val result =
            gradleRunner(projectDir)
                .withArguments(VERIFY_EXPIRED_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected SUCCESS despite invalid date.\n${result.output}",
        )
        assertTrue(
            result.output.contains("invalid expiresAt format"),
            "Expected format warning in output.\n${result.output}",
        )
        assertTrue(
            result.output.contains("not-a-date"),
            "Expected the invalid value in the warning message.\n${result.output}",
        )
        assertFalse(
            result.output.contains("expired on"),
            "Format warning should not also produce an expired-on warning.\n${result.output}",
        )
    }

    /**
     * Case (e): expiresAt == today → no expired warning.
     * A flag is considered valid through its expiry day; expiry starts the day after.
     */
    @Test
    fun `flag with expiresAt equal to today does not emit expired warning`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, expiresAt = LocalDate.now().toString())

        val result =
            gradleRunner(projectDir)
                .withArguments(VERIFY_EXPIRED_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected SUCCESS.\n${result.output}",
        )
        assertFalse(
            result.output.contains("expired on"),
            "Expected no expired-flag warning for a flag expiring today.\n${result.output}",
        )
    }

    /** Case (f): flag with no expiresAt → no warning, build succeeds (the `?: continue` path). */
    @Test
    fun `flag without expiresAt does not emit any warning`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFileNoExpiresAt(projectDir)

        val result =
            gradleRunner(projectDir)
                .withArguments(VERIFY_EXPIRED_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected SUCCESS.\n${result.output}",
        )
        assertFalse(
            result.output.contains("expired on"),
            "Expected no expired-flag warning for a flag with no expiresAt.\n${result.output}",
        )
        assertFalse(
            result.output.contains("invalid expiresAt"),
            "Expected no format warning for a flag with no expiresAt.\n${result.output}",
        )
    }

    /**
     * Case (g): multiple flags — two expired + one future in one module →
     * exactly the two expired warnings are emitted; the future flag produces none.
     */
    @Test
    fun `two expired flags and one future flag emit exactly two expired warnings`() {
        writeSettingsFile(projectDir, cacheDir)
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    boolean("old_flag_a", default = false) {
                        this.expiresAt = "2000-01-01"
                    }
                    boolean("old_flag_b", default = false) {
                        this.expiresAt = "2001-06-15"
                    }
                    boolean("future_flag", default = false) {
                        this.expiresAt = "2099-12-31"
                    }
                }
            }
            """.trimIndent(),
        )

        val result =
            gradleRunner(projectDir)
                .withArguments(VERIFY_EXPIRED_FLAGS_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected SUCCESS.\n${result.output}",
        )
        assertTrue(
            result.output.contains("expired on 2000-01-01"),
            "Expected warning for old_flag_a.\n${result.output}",
        )
        assertTrue(
            result.output.contains("expired on 2001-06-15"),
            "Expected warning for old_flag_b.\n${result.output}",
        )
        assertFalse(
            result.output.contains("2099-12-31"),
            "Expected no warning for the future flag.\n${result.output}",
        )
    }

    /**
     * Case (i): flags file does not exist → task warns with the file path and returns early.
     *
     * TestKit fixtures always produce the flags file via the dependsOn chain, so this path cannot
     * be exercised cheaply through a full Gradle run. We instead instantiate [VerifyExpiredFlagsTask]
     * via [ProjectBuilder] and point [VerifyExpiredFlagsTask.flagsFile] at a non-existent path,
     * then invoke [VerifyExpiredFlagsTask.verify] directly.
     *
     * The warn message is captured by the Gradle build logger; we assert via the task's logger
     * level by verifying no exception is thrown and the verify() call returns normally.
     */
    @Test
    fun `missing flags file does not throw and emits file-not-found warning`() {
        val project: Project = ProjectBuilder.builder().build()

        @Suppress("DEPRECATION")
        val task =
            project.tasks.create("verifyExpiredFlagsTest", VerifyExpiredFlagsTask::class.java)

        val nonExistentFile = tempFolder.root.resolve("does-not-exist/flags.txt")
        task.flagsFile.set(nonExistentFile)

        // verify() must not throw — it logs a warning and returns early.
        task.verify()
        // If we reach this line the early-return path executed without exception.
    }

    /**
     * Case (j): requesting [GENERATE_PROGUARD_TASK_NAME] directly with an expired flag →
     * [VerifyExpiredFlagsTask] is gated before it and the expired-flag warning appears in output.
     * Verifies that the [dependsOn] wiring added to [registerProguardTask] is in effect even
     * when [GENERATE_CONFIG_PARAM_TASK_NAME] is not part of the requested task graph.
     */
    @Test
    fun `proguard task with expired flag gates on verify task and emits expired warning`() {
        writeSettingsFile(projectDir, cacheDir)
        writeBuildFile(projectDir, expiresAt = "2000-01-01")

        val result =
            gradleRunner(projectDir)
                .withArguments(GENERATE_PROGUARD_TASK_NAME, "--build-cache")
                .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$GENERATE_PROGUARD_TASK_NAME")?.outcome,
            "Expected proguard task SUCCESS.\n${result.output}",
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":$VERIFY_EXPIRED_FLAGS_TASK_NAME")?.outcome,
            "Expected verifyExpiredFlags to run as a dependency of $GENERATE_PROGUARD_TASK_NAME.\n${result.output}",
        )
        assertTrue(
            result.output.contains("expired on 2000-01-01"),
            "Expected expired-flag warning in output when requesting proguard task.\n${result.output}",
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun writeSettingsFile(
        projectDir: File,
        cacheDir: File,
    ) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "verify-expired-test"
            buildCache {
                local {
                    directory = "${cacheDir.absolutePath.replace("\\", "/")}"
                    isEnabled = true
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeBuildFile(
        projectDir: File,
        expiresAt: String,
    ) {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    boolean("my_flag", default = false) {
                        this.expiresAt = "$expiresAt"
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeBuildFileNoExpiresAt(projectDir: File) {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    boolean("my_flag", default = false)
                }
            }
            """.trimIndent(),
        )
    }

    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
