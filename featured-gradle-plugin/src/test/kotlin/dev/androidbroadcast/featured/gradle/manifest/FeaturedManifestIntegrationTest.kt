package dev.androidbroadcast.featured.gradle.manifest

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
 * Integration tests for the per-module Featured manifest generation using the
 * `manifest-publish-project` fixture (Android library with local and remote flags).
 *
 * Skipped when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is not set.
 */
class FeaturedManifestIntegrationTest {
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

        projectDir = tempFolder.newFolder("manifest-publish-project")
        copyManifestFixture("manifest-publish-project", projectDir)
        // invariantSeparatorsPath replaces backslashes with forward slashes — Java's `.properties`
        // parser treats backslashes as escape characters, so a raw Windows SDK path would corrupt
        // local.properties.
        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir!!.invariantSeparatorsPath}\n")
    }

    @Test
    fun `generateFeaturedManifest produces manifest with correct content`() {
        val result =
            gradleRunner()
                .withArguments(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME", "--stacktrace")
                .build()

        val outcome = result.task(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected :app:$GENERATE_FEATURED_MANIFEST_TASK_NAME to succeed, got $outcome\n${result.output}",
        )

        val manifest = readManifest()
        assertEquals(SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(":app", manifest.modulePath)
        assertEquals(3, manifest.flags.size, "Expected 3 flags (dark_mode, checkout_variant, promo_banner)")

        val darkMode = manifest.flags.first { it.key == "dark_mode" }
        assertEquals(FlagKind.LOCAL, darkMode.kind)
        assertEquals(ValueType.BOOLEAN, darkMode.valueType)

        val promoBanner = manifest.flags.first { it.key == "promo_banner" }
        assertEquals(FlagKind.REMOTE, promoBanner.kind)
        assertEquals(ValueType.BOOLEAN, promoBanner.valueType)

        val checkoutVariant = manifest.flags.first { it.key == "checkout_variant" }
        assertEquals(FlagKind.LOCAL, checkoutVariant.kind)
        assertEquals(ValueType.ENUM, checkoutVariant.valueType)
        assertEquals("com.example.CheckoutVariant", checkoutVariant.enumTypeFqn)
    }

    @Test
    fun `second run without changes reports UP_TO_DATE`() {
        gradleRunner()
            .withArguments(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")
            .build()

        val result =
            gradleRunner()
                .withArguments(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")
                .build()

        val outcome = result.task(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")?.outcome
        assertTrue(
            outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE,
            "Expected :app:$GENERATE_FEATURED_MANIFEST_TASK_NAME to be UP_TO_DATE or FROM_CACHE on second run, got $outcome",
        )
    }

    @Test
    fun `adding a new flag invalidates the task`() {
        gradleRunner()
            .withArguments(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")
            .build()

        // Append a new local flag to the app build script to invalidate inputs.
        val buildFile = projectDir.resolve("app/build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                "enum(\"checkout_variant\", typeFqn = \"com.example.CheckoutVariant\", default = \"LEGACY\")",
                "enum(\"checkout_variant\", typeFqn = \"com.example.CheckoutVariant\", default = \"LEGACY\")\n" +
                    "        int(\"max_retries\", default = 3)",
            ),
        )

        val result =
            gradleRunner()
                .withArguments(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")
                .build()

        val outcome = result.task(":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected :app:$GENERATE_FEATURED_MANIFEST_TASK_NAME to re-run after input change, got $outcome",
        )

        val manifest = readManifest()
        assertEquals(4, manifest.flags.size, "Expected 4 flags after adding max_retries")
    }

    @Test
    fun `configuration cache stores on first run`() {
        val result =
            gradleRunner()
                .withArguments(
                    ":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME",
                    "--configuration-cache",
                    "--configuration-cache-problems=warn",
                ).build()

        // Gradle does not create build/reports/configuration-cache/ unless there are CC problems
        // to report. The canonical signal that the cache was stored is the output line.
        assertTrue(
            result.output.contains("Configuration cache entry stored"),
            "Expected 'Configuration cache entry stored' in output, got:\n${result.output}",
        )
    }

    @Test
    fun `configuration cache is reused on second run`() {
        gradleRunner()
            .withArguments(
                ":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME",
                "--configuration-cache",
                "--configuration-cache-problems=warn",
            ).build()

        val secondRun =
            gradleRunner()
                .withArguments(
                    ":app:$GENERATE_FEATURED_MANIFEST_TASK_NAME",
                    "--configuration-cache",
                    "--configuration-cache-problems=warn",
                ).build()

        assertTrue(
            secondRun.output.contains("Configuration cache entry reused") ||
                secondRun.output.contains("Reusing configuration cache"),
            "Expected CC reuse marker in second-run output, got:\n${secondRun.output}",
        )
    }

    // Configuration exposure (consumable flags, Usage / schema-major attributes, outgoing
    // artifact and task dependency) is covered by FeaturedManifestConfigurationTest via
    // ProjectBuilder — verifying that here through `:outgoingVariants` triggers a known
    // ConcurrentModificationException in AGP 9.1.0 when Android's per-variant configurations
    // are iterated alongside our consumable one.

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun readManifest(): FeaturedManifest {
        val file = projectDir.resolve("app/build/featured/featured-manifest.json")
        assertTrue(file.exists(), "Expected featured-manifest.json at ${file.path}")
        return FeaturedManifestJson.decodeFromString<FeaturedManifest>(file.readText())
    }

    private fun gradleRunner(): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
