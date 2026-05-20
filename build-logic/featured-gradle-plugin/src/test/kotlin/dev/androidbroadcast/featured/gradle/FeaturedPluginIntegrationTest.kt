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
 * 2. Auto-wires that file into the AGP release variant so the `generateFeaturedProguardRules`
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
    fun `generateFeaturedProguardRules task produces correct assumevalues rule for boolean local flag`() {
        val result =
            gradleRunner(projectDir)
                .withArguments("generateFeaturedProguardRules", "--stacktrace")
                .build()

        val outcome = result.task(":generateFeaturedProguardRules")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected :generateFeaturedProguardRules to succeed, got $outcome\n${result.output}",
        )

        val proFile = projectDir.resolve("build/featured/proguard-featured.pro")
        assertTrue(proFile.exists(), "Expected proguard-featured.pro to be generated at ${proFile.path}")

        val content = proFile.readText()
        assertContainsAssumevaluesBlock(content)
    }

    @Test
    fun `assembleRelease without configuration cache wires proguard rules and completes successfully`() {
        runAssembleReleaseAndAssert(cc = false)
    }

    /**
     * Parametrization for AC-1 / AC-2: same assembleRelease assertions, but with
     * `--configuration-cache --configuration-cache-problems=fail` and a second run
     * that MUST reuse the cache.
     *
     * Cache reuse signal: directory-snapshot of `build/reports/configuration-cache/`
     * top-level subdirectories. Run 1 must STORE (exactly one new subdir appears);
     * run 2 must LOAD (no new subdir appears). Per spec, free-text grep of TestKit
     * output and HTML/JSON report parsing are explicitly forbidden as cache-reuse
     * signals — neither is a Gradle public API contract.
     */
    @Test
    fun `assembleRelease with configuration cache stores then reuses the cache`() {
        val before = ccHashDirs(projectDir)

        val firstRun = runAssembleReleaseAndAssert(cc = true)
        val afterRun1 = ccHashDirs(projectDir)
        assertTrue(
            afterRun1.isNotEmpty(),
            "build/reports/configuration-cache/ MUST exist after run 1 — empty set means CC was not enabled by the build at all.\n${firstRun.output}",
        )
        val newRun1 = afterRun1 - before
        assertEquals(
            1,
            newRun1.size,
            "First CC run must STORE — expected exactly one new top-level subdir under build/reports/configuration-cache/, got new=$newRun1 (before=$before, after=$afterRun1)\n${firstRun.output}",
        )

        val secondRun = runAssembleReleaseAndAssert(cc = true)
        val afterRun2 = ccHashDirs(projectDir)
        assertTrue(
            afterRun2.isNotEmpty(),
            "build/reports/configuration-cache/ MUST exist after run 2 — empty set means CC was not enabled by the build at all.\n${secondRun.output}",
        )
        assertEquals(
            afterRun1,
            afterRun2,
            "Second CC run must LOAD (reuse) — no new top-level subdir expected. Delta: ${afterRun2 - afterRun1}\n${secondRun.output}",
        )
    }

    // ── Shared helpers for AC-1 parametrization ───────────────────────────────

    /**
     * Runs `assembleRelease`, optionally with CC flags, and asserts the same outcomes
     * for both parametrized scenarios. Returns the [BuildResult] so callers can layer
     * extra CC-specific assertions (e.g. directory snapshots) on top.
     */
    private fun runAssembleReleaseAndAssert(cc: Boolean): org.gradle.testkit.runner.BuildResult {
        val args =
            buildList {
                add("assembleRelease")
                add("--stacktrace")
                if (cc) {
                    add("--configuration-cache")
                    add("--configuration-cache-problems=fail")
                }
            }

        val result =
            gradleRunner(projectDir)
                .withArguments(args)
                .build()

        // generateFeaturedProguardRules must have run as part of the release build.
        val proguardOutcome = result.task(":generateFeaturedProguardRules")?.outcome
        assertTrue(
            proguardOutcome == TaskOutcome.SUCCESS ||
                proguardOutcome == TaskOutcome.UP_TO_DATE ||
                proguardOutcome == TaskOutcome.FROM_CACHE,
            "Expected :generateFeaturedProguardRules to participate in assembleRelease (cc=$cc), got $proguardOutcome\n${result.output}",
        )

        // On the second CC-enabled run, the cache is reused AND all task outputs are unchanged,
        // so :assembleRelease reports UP_TO_DATE rather than SUCCESS. Both outcomes mean "build
        // completed without re-doing work that did not need to be re-done"; either is acceptable.
        val assembleOutcome = result.task(":assembleRelease")?.outcome
        assertTrue(
            assembleOutcome == TaskOutcome.SUCCESS || assembleOutcome == TaskOutcome.UP_TO_DATE,
            "Expected :assembleRelease to succeed or be up-to-date (cc=$cc), got $assembleOutcome\n${result.output}",
        )

        // Verify the .pro file content is correct even after the full build.
        val proFile = projectDir.resolve("build/featured/proguard-featured.pro")
        assertTrue(proFile.exists(), "Expected proguard-featured.pro to exist after assembleRelease (cc=$cc)")
        assertContainsAssumevaluesBlock(proFile.readText())

        return result
    }

    /**
     * Snapshot the set of top-level subdirectory names under
     * `build/reports/configuration-cache/`. Each name is a Gradle CC hash directory;
     * a new directory appearing across runs signals a STORE, an unchanged set signals
     * a LOAD (cache reuse). See AC-1 in the spec.
     */
    private fun ccHashDirs(projectDir: File): Set<String> {
        val root = projectDir.resolve("build/reports/configuration-cache")
        if (!root.exists()) return emptySet()
        return root
            .listFiles { f -> f.isDirectory }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /**
     * Asserts that [content] contains a well-formed `-assumevalues` block targeting the
     * extensions class for the root module (`:`) and the `dark_mode` boolean flag,
     * and that the enum flag `checkout_variant` is NOT present in the rules.
     *
     * Expected output (from [ProguardRulesGenerator]):
     * ```proguard
     * -assumevalues class dev.androidbroadcast.featured.generated.GeneratedFlagExtensionsRootKt {
     *     boolean isDarkModeEnabled(dev.androidbroadcast.featured.ConfigValues) return false;
     * }
     * ```
     *
     * The root module path `:` produces the file suffix `Root` via [String.modulePathToFileSuffix],
     * so the Kotlin file is `GeneratedFlagExtensionsRoot.kt` and the JVM class name
     * (Kotlin's file-to-class convention) is `GeneratedFlagExtensionsRootKt`.
     *
     * Enum flags (`checkout_variant`) must not appear in `-assumevalues` rules — their values
     * are resolved at runtime from providers and cannot be assumed at build time (issue #162).
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
        assertTrue(
            !content.contains("checkoutVariant"),
            "Enum flag 'checkout_variant' must not appear in -assumevalues rules\nActual content:\n$content",
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
        // modulePathToFileSuffix(":") → "Root" → fileName → "GeneratedFlagExtensionsRoot.kt"
        // → JVM class: "GeneratedFlagExtensionsRootKt"
        const val EXTENSIONS_FQN =
            "dev.androidbroadcast.featured.generated.GeneratedFlagExtensionsRootKt"
        const val CONFIG_VALUES_FQN = "dev.androidbroadcast.featured.ConfigValues"
        const val IS_DARK_MODE_ENABLED = "isDarkModeEnabled"
    }
}
