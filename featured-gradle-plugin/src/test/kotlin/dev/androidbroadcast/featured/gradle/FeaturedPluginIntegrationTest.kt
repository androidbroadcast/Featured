package dev.androidbroadcast.featured.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end integration test that verifies the Featured Gradle plugin:
 * 1. Generates a ProGuard file at `build/featured/proguard-featured.pro` with correct
 *    `-assumevalues` rules for declared local flags.
 * 2. Auto-wires that file into the AGP release variant so the `generateProguardRules`
 *    task participates in `assembleRelease`.
 *
 * The test uses a minimal Android application fixture copied from
 * `src/test/fixtures/android-project/`. It runs via Gradle TestKit with the plugin
 * classpath injected automatically by the `java-gradle-plugin` metadata.
 *
 * Skipped when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is not set — the test requires a
 * real Android SDK to compile the AGP-driven release build.
 */
class FeaturedPluginIntegrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        val sdkDir = androidSdkDir()
        assumeTrue(
            "ANDROID_HOME or ANDROID_SDK_ROOT must be set to run integration tests",
            sdkDir != null,
        )

        projectDir = tempFolder.newFolder("android-project")
        copyFixture(projectDir)

        // Write local.properties with sdk.dir so AGP can locate the Android SDK.
        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir!!.absolutePath}\n")
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `generateProguardRules task produces correct assumevalues rule for boolean local flag`() {
        val result =
            gradleRunner(projectDir)
                .withArguments("generateProguardRules", "--stacktrace")
                .build()

        val outcome = result.task(":generateProguardRules")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected :generateProguardRules to succeed, got $outcome\n${result.output}",
        )

        val proFile = projectDir.resolve("build/featured/proguard-featured.pro")
        assertTrue(proFile.exists(), "Expected proguard-featured.pro to be generated at ${proFile.path}")

        val content = proFile.readText()
        assertContainsAssumevaluesBlock(content)
    }

    @Test
    fun `assembleRelease wires proguard rules and completes successfully`() {
        val result =
            gradleRunner(projectDir)
                .withArguments("assembleRelease", "--stacktrace")
                .build()

        // generateProguardRules must have run as part of the release build.
        val proguardOutcome = result.task(":generateProguardRules")?.outcome
        assertTrue(
            proguardOutcome == TaskOutcome.SUCCESS || proguardOutcome == TaskOutcome.UP_TO_DATE,
            "Expected :generateProguardRules to participate in assembleRelease, got $proguardOutcome\n${result.output}",
        )

        val assembleOutcome = result.task(":assembleRelease")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            assembleOutcome,
            "Expected :assembleRelease to succeed, got $assembleOutcome\n${result.output}",
        )

        // Verify the .pro file content is correct even after the full build.
        val proFile = projectDir.resolve("build/featured/proguard-featured.pro")
        assertTrue(proFile.exists(), "Expected proguard-featured.pro to exist after assembleRelease")
        assertContainsAssumevaluesBlock(proFile.readText())
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /**
     * Asserts that [content] contains a well-formed `-assumevalues` block targeting the
     * extensions class for the root module (`:`) and the `dark_mode` boolean flag.
     *
     * Expected output (from [ProguardRulesGenerator]):
     * ```proguard
     * -assumevalues class dev.androidbroadcast.featured.generated.FeaturedRoot_FlagExtensionsKt {
     *     boolean isDarkModeEnabled(dev.androidbroadcast.featured.ConfigValues) return false;
     * }
     * ```
     *
     * The root module path `:` produces the identifier `Root` via [String.modulePathToIdentifier],
     * so the JVM class name is `FeaturedRoot_FlagExtensionsKt`.
     */
    private fun assertContainsAssumevaluesBlock(content: String) {
        assertTrue(
            content.contains("-assumevalues class $EXTENSIONS_FQN {"),
            "Expected -assumevalues block targeting $EXTENSIONS_FQN\nActual content:\n$content",
        )
        assertTrue(
            content.contains("boolean $IS_DARK_MODE_ENABLED($CONFIG_VALUES_FQN) return false;"),
            "Expected 'boolean $IS_DARK_MODE_ENABLED($CONFIG_VALUES_FQN) return false;' in rules\nActual content:\n$content",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the Android SDK directory from environment, or null if not set. */
    private fun androidSdkDir(): File? {
        val path =
            System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
                ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
                ?: return null
        return File(path).takeIf { it.isDirectory }
    }

    /**
     * Copies the fixture project from `src/test/fixtures/android-project/` into [dest].
     *
     * The fixture is located relative to the plugin module's project directory, which
     * Gradle TestKit passes as the working directory when running tests.
     */
    private fun copyFixture(dest: File) {
        val fixtureSource = fixtureDir()
        fixtureSource
            .walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relative = file.relativeTo(fixtureSource)
                val target = dest.resolve(relative)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }
    }

    /**
     * Resolves the fixture directory. The plugin module's project directory is either
     * injected as the `user.dir` system property by Gradle's test task, or derived
     * relative to this class file's location.
     */
    private fun fixtureDir(): File {
        // Gradle's test task sets user.dir to the module project directory.
        val moduleDir = File(System.getProperty("user.dir"))
        val candidate = moduleDir.resolve("src/test/fixtures/android-project")
        require(candidate.isDirectory) {
            "Fixture directory not found at ${candidate.absolutePath}. " +
                "Expected it relative to module project dir: ${moduleDir.absolutePath}"
        }
        return candidate
    }

    /**
     * Creates a [GradleRunner] for the fixture project.
     *
     * AGP is declared as `compileOnly` in this module — the applying build provides it at runtime.
     * In the TestKit subprocess, AGP is loaded by the build's own classloader when `com.android.application`
     * is applied from the fixture's `plugins {}` block (resolved from Google Maven). The Featured plugin
     * code that references [com.android.build.api.variant.AndroidComponentsExtension] is loaded in the
     * same classloader context, so no extra classpath injection is needed.
     */
    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        // The fixture is a single-project (root) build.
        // modulePathToIdentifier(":") → "Root" → jvmFileName → "FeaturedRoot_FlagExtensionsKt"
        const val EXTENSIONS_FQN =
            "dev.androidbroadcast.featured.generated.FeaturedRoot_FlagExtensionsKt"
        const val CONFIG_VALUES_FQN = "dev.androidbroadcast.featured.ConfigValues"
        const val IS_DARK_MODE_ENABLED = "isDarkModeEnabled"
    }
}
