package dev.androidbroadcast.featured.gradle

import dev.androidbroadcast.featured.gradle.manifest.androidSdkDirOrNull
import dev.androidbroadcast.featured.gradle.manifest.copyManifestFixture
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertTrue

/**
 * Verifies that `dev.androidbroadcast.featured` (per-module `generateConfigParam`) and
 * `dev.androidbroadcast.featured.application` (`generateFeaturedRegistry`) auto-wire their generated
 * `build/generated/featured/commonMain` directory into the consumer module's compilation, across
 * each supported plugin shape: Kotlin/JVM, KMP, plain-AGP application, plain-AGP library, and the
 * aggregator. Consumers should need ZERO manual `srcDir` / `dependsOn`.
 *
 * **Why compilation instead of a src-dir probe.** Each fixture ships a `WiringProof.kt` that
 * references the auto-wired generated object (`GeneratedLocalFlagsRoot` / `GeneratedFeaturedRegistry`)
 * plus a minimal `FeaturedRuntimeStub.kt` standing in for the `:core` types the generated sources
 * import (`:core` is a sibling project, not resolvable from a TestKit fixture). Compiling the module
 * therefore succeeds ONLY if the plugin wired the generated directory into the compiled source set
 * AND ordered the producer task ahead of compilation. A green compile is the wiring assertion —
 * strictly stronger than reading a source-set directory list, which could list a directory that is
 * never actually compiled.
 *
 * The generated directory depends on the plugin: `build/generated/featured/commonMain` for
 * `generateConfigParam` (per-module, see [GenerateConfigParamTask]) and
 * `build/generated/featured/registry` for `generateFeaturedRegistry` (the aggregator, see
 * GenerateFeaturedRegistryTask) — distinct directories so the two outputs never overlap when a
 * module applies both plugins.
 *
 * AGP-based cases skip when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is unset, matching the rest of the
 * integration suite.
 */
class GeneratedSourceWiringTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── Kotlin/JVM ────────────────────────────────────────────────────────────

    @Test
    fun `kotlin-jvm compiles auto-wired generated sources`() {
        val projectDir = prepareFixture("wiring-kotlin-jvm-project")

        val result =
            gradleRunner(projectDir)
                .withArguments("compileKotlin", "--stacktrace", "--no-build-cache")
                .build()

        assertGeneratedSourceTaskRan(result, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        assertTaskSucceeded(result, ":compileKotlin")
    }

    // ── KMP ─────────────────────────────────────────────────────────────────

    @Test
    fun `kmp compiles auto-wired generated sources in commonMain`() {
        val projectDir = prepareFixture("wiring-kmp-project")

        val result =
            gradleRunner(projectDir)
                .withArguments("compileKotlinJvm", "--stacktrace", "--no-build-cache")
                .build()

        assertGeneratedSourceTaskRan(result, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        assertTaskSucceeded(result, ":compileKotlinJvm")
    }

    // ── Plain AGP application ─────────────────────────────────────────────────

    @Test
    fun `android application compiles auto-wired generated sources`() {
        assumeAndroidSdk()
        val projectDir = prepareAndroidFixture("wiring-android-app-project")

        val result =
            gradleRunner(projectDir)
                .withArguments("compileDebugKotlin", "--stacktrace", "--no-build-cache")
                .build()

        assertGeneratedSourceTaskRan(result, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        assertTaskSucceeded(result, ":compileDebugKotlin")
    }

    // ── Plain AGP library ─────────────────────────────────────────────────────

    @Test
    fun `android library compiles auto-wired generated sources`() {
        assumeAndroidSdk()
        val projectDir = prepareAndroidFixture("wiring-android-library-project")

        val result =
            gradleRunner(projectDir)
                .withArguments("compileDebugKotlin", "--stacktrace", "--no-build-cache")
                .build()

        assertGeneratedSourceTaskRan(result, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        assertTaskSucceeded(result, ":compileDebugKotlin")
    }

    // ── Aggregator (registry) ─────────────────────────────────────────────────

    @Test
    fun `aggregator compiles auto-wired generated registry without manual srcDir`() {
        assumeAndroidSdk()
        val projectDir = prepareAndroidFixture("aggregator-multi-module-project")

        val result =
            gradleRunner(projectDir)
                .withArguments(":app:compileDebugKotlin", "--stacktrace", "--no-build-cache")
                .build()

        // The aggregator plugin must auto-wire generateFeaturedRegistry; compiling the app must
        // therefore pull that producer task in as a transitive dependency and compile its output.
        assertGeneratedSourceTaskRan(result, ":app:generateFeaturedRegistry")
        assertTaskSucceeded(result, ":app:compileDebugKotlin")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun prepareFixture(name: String): File {
        val dir = tempFolder.newFolder(name)
        copyManifestFixture(name, dir)
        return dir
    }

    private fun prepareAndroidFixture(name: String): File {
        val dir = prepareFixture(name)
        val sdkDir = androidSdkDirOrNull()!!
        dir.resolve("local.properties").writeText("sdk.dir=${sdkDir.invariantSeparatorsPath}\n")
        return dir
    }

    private fun assumeAndroidSdk() {
        assumeTrue(
            "ANDROID_HOME or ANDROID_SDK_ROOT must be set to run Android wiring tests",
            androidSdkDirOrNull() != null,
        )
    }

    /**
     * Asserts the generated-source producer task ([taskPath]) actually ran as a dependency of the
     * compile task — proving the wiring registered the source dir AS a producer-backed input, not as
     * a bare path. A null outcome means the task was not in the graph (wiring failed).
     */
    private fun assertGeneratedSourceTaskRan(
        result: BuildResult,
        taskPath: String,
    ) {
        val outcome = result.task(taskPath)?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS ||
                outcome == TaskOutcome.UP_TO_DATE ||
                outcome == TaskOutcome.FROM_CACHE,
            "Expected producer task $taskPath to run as a dependency of compilation (got $outcome). " +
                "A null outcome means the generated dir was not wired with a task dependency.\n${result.output}",
        )
    }

    /**
     * Asserts the compile task ([taskPath]) ran and succeeded — i.e. the auto-wired generated
     * sources were on the compiled source set and compiled cleanly against the fixture's `:core`
     * stub. A failed or absent compile means the generated directory was not wired into the
     * compilation.
     */
    private fun assertTaskSucceeded(
        result: BuildResult,
        taskPath: String,
    ) {
        val outcome = result.task(taskPath)?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS ||
                outcome == TaskOutcome.UP_TO_DATE ||
                outcome == TaskOutcome.FROM_CACHE,
            "Expected compile task $taskPath to succeed, proving the generated dir was compiled " +
                "(got $outcome).\n${result.output}",
        )
    }

    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
