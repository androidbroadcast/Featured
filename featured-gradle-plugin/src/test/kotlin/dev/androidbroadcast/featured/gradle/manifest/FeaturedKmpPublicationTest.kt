package dev.androidbroadcast.featured.gradle.manifest

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Smoke test that verifies the `featuredManifest` consumable configuration does NOT leak
 * into the published Gradle Module Metadata (`.module` JSON) for a KMP module.
 *
 * Custom consumable configurations with arbitrary `Usage` attributes are not auto-published
 * by the `kotlinMultiplatform`, `java`, `java-library`, or AGP software components — each
 * component exposes only the variants it explicitly added via `addVariantsFromConfiguration`.
 * This test is the mandatory gate that confirms that invariant in practice for KMP.
 *
 * Uses the `kmp-publish-project` fixture (JVM-only KMP module) to avoid requiring the
 * Kotlin/Native toolchain download that `iosX64()` would trigger on CI.
 */
class FeaturedKmpPublicationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `publishing KMP module does not expose featuredManifest variant in module metadata`() {
        val projectDir = tempFolder.newFolder("kmp-publish-project")
        copyManifestFixture("kmp-publish-project", projectDir)

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(":module:publishAllPublicationsToTestLocalRepository", "--stacktrace")
                .forwardOutput()
                .build()

        val outcome = result.task(":module:publishAllPublicationsToTestLocalRepository")?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
            "Expected publish task to succeed, got $outcome\n${result.output}",
        )

        // Locate the generated .module file in the test-local repo.
        val repoDir = projectDir.resolve("module/build/test-repo")
        val moduleFiles = repoDir.walkTopDown().filter { it.extension == "module" }.toList()
        assertTrue(
            moduleFiles.isNotEmpty(),
            "Expected at least one .module file in ${repoDir.path}; found none.\n${result.output}",
        )

        moduleFiles.forEach { moduleFile ->
            val moduleJson = moduleFile.readText()

            // The featuredManifest Usage must not appear in any published variant.
            assertFalse(
                moduleJson.contains(FEATURED_MANIFEST_USAGE),
                "Found '$FEATURED_MANIFEST_USAGE' in published .module metadata at ${moduleFile.path}.\n" +
                    "The featuredManifest configuration must be excluded from Maven publication.\n" +
                    "Content:\n$moduleJson",
            )

            // Sanity check: the .module file is valid and has variants.
            assertTrue(
                moduleJson.contains("\"variants\""),
                "Expected 'variants' key in .module metadata at ${moduleFile.path}",
            )
        }
    }
}
