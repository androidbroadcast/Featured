package dev.androidbroadcast.featured.gradle.manifest

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that applying the Featured plugin without any `featured { }` DSL block generates
 * a manifest with an empty `flags` array (not omitted) and the correct `schemaVersion`.
 *
 * Uses Gradle TestKit because `afterEvaluate` (which wires the DSL into the resolve task)
 * is not triggered by ProjectBuilder — only a real Gradle execution resolves the full lifecycle.
 */
class FeaturedManifestEmptyDslTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `generateFeaturedManifest with no DSL block produces manifest with empty flags array`() {
        val projectDir = tempFolder.newFolder("jvm-empty-featured-project")
        copyManifestFixture("jvm-empty-featured-project", projectDir)

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(GENERATE_FEATURED_MANIFEST_TASK_NAME, "--stacktrace")
                .forwardOutput()
                .build()

        val outcome = result.task(":$GENERATE_FEATURED_MANIFEST_TASK_NAME")?.outcome
        assertEquals(
            TaskOutcome.SUCCESS,
            outcome,
            "Expected :$GENERATE_FEATURED_MANIFEST_TASK_NAME to succeed, got $outcome\n${result.output}",
        )

        val manifestFile = projectDir.resolve("build/featured/featured-manifest.json")
        assertTrue(manifestFile.exists(), "Expected featured-manifest.json to be generated at ${manifestFile.path}")

        val rawJson = manifestFile.readText()

        // Parse and verify schema.
        val manifest = FeaturedManifestJson.decodeFromString<FeaturedManifest>(rawJson)
        assertEquals(SCHEMA_VERSION, manifest.schemaVersion)
        // Plugin is applied to the rootProject in this single-module fixture, so the
        // captured Project.path is ":". This verifies the contract for root-project apply.
        assertEquals(":", manifest.modulePath, "Expected modulePath ':' for root project apply")
        assertTrue(manifest.flags.isEmpty(), "Expected empty flags list, got: ${manifest.flags}")

        // Verify the raw JSON contains "flags": [] explicitly — not omitted.
        assertTrue(
            rawJson.contains("\"flags\": []"),
            "Expected 'flags': [] in raw JSON — empty list must not be omitted, got:\n$rawJson",
        )
    }
}
