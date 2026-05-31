package dev.androidbroadcast.featured.gradle.aggregation

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/**
 * Verifies that a corrupt or malformed manifest file produces an [IllegalStateException]
 * whose message includes the file path so the developer can locate the bad file immediately.
 *
 * Paired with Fix 3 in GenerateFeaturedRegistryTask.
 */
@Suppress("UnstableApiUsage")
class FeaturedAggregationParseErrorTest {
    @Test
    fun `malformed manifest json produces IllegalStateException containing file path`() {
        val tempDir = Files.createTempDirectory("featured-parse-error-test").toFile()
        try {
            val badManifest =
                File(tempDir, "featured-manifest.json").also {
                    it.writeText("""{ "broken": json""")
                }
            val outputFile = File(tempDir, "GeneratedFeaturedRegistry.kt")

            val project = ProjectBuilder.builder().build()
            project.plugins.apply("dev.androidbroadcast.featured.application")

            val task = project.tasks.findByName(GENERATE_FEATURED_REGISTRY_TASK_NAME) as GenerateFeaturedRegistryTask
            task.manifestFiles.from(badManifest)
            task.outputPackage.set(FEATURED_REGISTRY_PACKAGE)
            task.outputFile.set(outputFile)

            val ex = assertFailsWith<IllegalStateException> { task.generate() }
            assertContains(
                ex.message ?: "",
                badManifest.path,
                message = "Exception message must include the path of the malformed manifest file",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
