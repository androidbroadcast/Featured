package dev.androidbroadcast.featured.gradle.aggregation

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
 * Integration tests for the multi-module registry aggregation using the
 * `aggregator-multi-module-project` fixture (two Android library modules + aggregating app).
 *
 * Skipped when `ANDROID_HOME` / `ANDROID_SDK_ROOT` is not set.
 */
class FeaturedAggregationIntegrationTest {
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

        projectDir = tempFolder.newFolder("aggregator-multi-module-project")
        copyManifestFixture(fixtureName = "aggregator-multi-module-project", dest = projectDir)

        // Write local.properties with the real SDK path — use invariantSeparatorsPath so that
        // a raw Windows SDK path would not corrupt local.properties.
        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir!!.invariantSeparatorsPath}\n")
    }

    @Test
    fun `generateFeaturedRegistry succeeds`() {
        val result =
            gradleRunner()
                .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME", "--stacktrace")
                .build()

        val outcome = result.task(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected :app:$GENERATE_FEATURED_REGISTRY_TASK_NAME to succeed, got $outcome\n${result.output}",
        )
    }

    @Test
    fun `generated file exists at expected path`() {
        gradleRunner()
            .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")
            .build()

        val generatedFile =
            projectDir.resolve(
                "app/build/generated/featured/commonMain/${FEATURED_REGISTRY_OBJECT}.kt",
            )
        assertTrue(generatedFile.exists(), "Expected generated file at ${generatedFile.path}")
    }

    @Test
    fun `generated source contains expected ConfigParam entries`() {
        gradleRunner()
            .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")
            .build()

        val source =
            projectDir
                .resolve("app/build/generated/featured/commonMain/${FEATURED_REGISTRY_OBJECT}.kt")
                .readText()

        assertTrue(source.contains("object $FEATURED_REGISTRY_OBJECT"), "Missing object declaration")
        assertTrue(source.contains("listOf("), "Missing listOf() in generated source")
        assertTrue(
            source.contains("ConfigParam<Boolean>(key = \"dark_mode\""),
            "Missing dark_mode (Boolean) entry",
        )
        assertTrue(
            source.contains("ConfigParam<com.example.CheckoutVariant>(key = \"checkout_variant\""),
            "Missing checkout_variant (ENUM) entry",
        )
        assertTrue(
            source.contains("ConfigParam<Boolean>(key = \"show_avatar\""),
            "Missing show_avatar (Boolean) entry",
        )
        assertTrue(
            source.contains("ConfigParam<String>(key = \"avatar_placeholder\""),
            "Missing avatar_placeholder (String) entry",
        )
    }

    @Test
    fun `second run without changes reports UP_TO_DATE`() {
        gradleRunner()
            .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")
            .build()

        val result =
            gradleRunner()
                .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")
                .build()

        val outcome = result.task(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")?.outcome
        assertTrue(
            outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE,
            "Expected UP_TO_DATE or FROM_CACHE on second run, got $outcome",
        )
    }

    @Test
    fun `mutating a feature module invalidates the registry task`() {
        gradleRunner()
            .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")
            .build()

        // Add a new flag to :feature-checkout to invalidate the manifest artifact.
        val buildFile = projectDir.resolve("feature-checkout/build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                "enum(\"checkout_variant\", typeFqn = \"com.example.CheckoutVariant\", default = \"LEGACY\")",
                "enum(\"checkout_variant\", typeFqn = \"com.example.CheckoutVariant\", default = \"LEGACY\")\n" +
                    "        int(\"max_retries\", default = 3)",
            ),
        )

        val result =
            gradleRunner()
                .withArguments(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")
                .build()

        val outcome = result.task(":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected SUCCESS after input change, got $outcome",
        )
    }

    @Test
    fun `configuration cache stores on first run`() {
        val result =
            gradleRunner()
                .withArguments(
                    ":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME",
                    "--configuration-cache",
                    "--configuration-cache-problems=warn",
                ).build()

        assertTrue(
            result.output.contains("Configuration cache entry stored"),
            "Expected 'Configuration cache entry stored' in output, got:\n${result.output}",
        )
    }

    @Test
    fun `configuration cache is reused on second run`() {
        gradleRunner()
            .withArguments(
                ":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME",
                "--configuration-cache",
                "--configuration-cache-problems=warn",
            ).build()

        val secondRun =
            gradleRunner()
                .withArguments(
                    ":app:$GENERATE_FEATURED_REGISTRY_TASK_NAME",
                    "--configuration-cache",
                    "--configuration-cache-problems=warn",
                ).build()

        assertTrue(
            secondRun.output.contains("Configuration cache entry reused") ||
                secondRun.output.contains("Reusing configuration cache"),
            "Expected CC reuse marker in second-run output, got:\n${secondRun.output}",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun gradleRunner(): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
