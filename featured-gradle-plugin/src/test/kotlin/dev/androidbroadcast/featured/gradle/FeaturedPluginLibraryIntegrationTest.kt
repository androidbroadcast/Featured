package dev.androidbroadcast.featured.gradle

import dev.androidbroadcast.featured.gradle.manifest.androidSdkDirOrNull
import dev.androidbroadcast.featured.gradle.manifest.copyManifestFixture
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
 * End-to-end integration test that verifies the Featured Gradle plugin correctly wires
 * generated `-assumevalues` rules as **consumer ProGuard rules** when applied to a
 * `com.android.library` module.
 *
 * Library modules do not run R8 themselves. The rules must be registered as
 * `consumerProguardFiles` so they are bundled into the AAR and forwarded to every
 * consuming app's R8, enabling dead-code elimination of disabled flag branches.
 *
 * The test uses a minimal Android library fixture copied from
 * `src/test/fixtures/android-library-project/`. It runs via Gradle TestKit with the plugin
 * classpath injected automatically by the `java-gradle-plugin` metadata.
 *
 * Skipped when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is not set — the test requires a
 * real Android SDK to run AGP tasks.
 */
class FeaturedPluginLibraryIntegrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        val sdkDir = androidSdkDirOrNull()
        assumeTrue(
            "ANDROID_HOME or ANDROID_SDK_ROOT must be set to run integration tests",
            sdkDir != null,
        )

        projectDir = tempFolder.newFolder("android-library-project")
        copyManifestFixture("android-library-project", projectDir)

        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir!!.invariantSeparatorsPath}\n")
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

        assertContainsAssumevaluesBlock(proFile.readText())
    }

    @Test
    fun `bundleReleaseAar wires consumer proguard rules and completes successfully`() {
        val result =
            gradleRunner(projectDir)
                .withArguments("bundleReleaseAar", "--stacktrace")
                .build()

        // generateFeaturedProguardRules must have run as part of the library release build.
        val proguardOutcome = result.task(":generateFeaturedProguardRules")?.outcome
        assertTrue(
            proguardOutcome == TaskOutcome.SUCCESS ||
                proguardOutcome == TaskOutcome.UP_TO_DATE ||
                proguardOutcome == TaskOutcome.FROM_CACHE,
            "Expected :generateFeaturedProguardRules to participate in bundleReleaseAar, got $proguardOutcome\n${result.output}",
        )

        val bundleOutcome = result.task(":bundleReleaseAar")?.outcome
        assertTrue(
            bundleOutcome == TaskOutcome.SUCCESS || bundleOutcome == TaskOutcome.UP_TO_DATE,
            "Expected :bundleReleaseAar to succeed or be up-to-date, got $bundleOutcome\n${result.output}",
        )

        // Verify the generated rules appear in the AAR's consumer proguard intermediates.
        // AGP 9.x merges consumer ProGuard rules into a single file via MergeConsumerProguardFilesTask.
        // The output lands at: build/intermediates/merged_consumer_proguard_file/release/<taskName>/proguard.txt
        // Older AGP used consumer_proguard_dir/release/ (directory with per-lib subdirs).
        // We search both roots so the test survives across AGP versions.
        val mergedRoot = projectDir.resolve("build/intermediates/merged_consumer_proguard_file/release")
        val legacyDir = projectDir.resolve("build/intermediates/consumer_proguard_dir/release")

        val consumerProguardFiles: List<File> =
            sequenceOf(mergedRoot, legacyDir)
                .filter { it.isDirectory }
                .flatMap { it.walkTopDown() }
                .filter { it.isFile && (it.name.endsWith(".pro") || it.name == "proguard.txt") }
                .toList()
        assertTrue(
            consumerProguardFiles.isNotEmpty(),
            "Expected consumer ProGuard files under ${mergedRoot.path} or ${legacyDir.path}, " +
                "but neither location was populated.\n${result.output}",
        )

        val combinedContent = consumerProguardFiles.joinToString("\n") { it.readText() }
        assertContainsAssumevaluesBlock(combinedContent)
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    /**
     * Asserts that [content] contains a well-formed `-assumevalues` block for the root module's
     * `dark_mode` boolean local flag.
     *
     * Expected output (from [ProguardRulesGenerator]):
     * ```proguard
     * -assumevalues class dev.androidbroadcast.featured.generated.GeneratedFlagExtensionsRootKt {
     *     boolean isDarkModeEnabled(dev.androidbroadcast.featured.ConfigValues) return false;
     * }
     * ```
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
