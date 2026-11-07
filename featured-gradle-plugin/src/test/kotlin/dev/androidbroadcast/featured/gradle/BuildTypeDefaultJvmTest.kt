package dev.androidbroadcast.featured.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * JVM replacements for the old in-process `discard()` rejection of `default = true`, plus the
 * no-Android `defaultFor` failure. These builds have no Android buildTypes container.
 */
class BuildTypeDefaultJvmTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `discard on a true base fails during configuration and does not emit the rule`() {
        val projectDir =
            jvmProject(
                """
                boolean("kept_on", default = true) {
                    discard("com.example.Feature")
                }
                """.trimIndent(),
            )

        val result = gradleRunner(projectDir).withArguments("generateFeaturedCheckDiscardRules", "--stacktrace").buildAndFail()

        assertContains(result.output, "kept_on")
        assertFalse(checkDiscardFile(projectDir).let { it.exists() && it.readText().contains("com.example.Feature") })
    }

    @Test
    fun `discard on a false base with no defaultFor still emits the class specification`() {
        val projectDir =
            jvmProject(
                """
                boolean("new_checkout", default = false) {
                    discard("com.example.Feature")
                }
                """.trimIndent(),
            )

        val result = gradleRunner(projectDir).withArguments("generateFeaturedCheckDiscardRules", "--stacktrace").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFeaturedCheckDiscardRules")?.outcome)
        assertContains(checkDiscardFile(projectDir).readText(), "-checkdiscard class com.example.Feature { *; }")
    }

    @Test
    fun `defaultFor without Android build types fails and names the flag`() {
        val projectDir =
            jvmProject(
                """
                boolean("debug_only", default = false) {
                    defaultFor("debug", true)
                }
                """.trimIndent(),
            )

        val result = gradleRunner(projectDir).withArguments("help", "--stacktrace").buildAndFail()

        assertContains(result.output, "debug_only")
        assertContains(result.output, "no Android build types")
    }

    private fun jvmProject(localFlags: String): File {
        val dir = tempFolder.newFolder()
        dir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "jvm-build-type-defaults"
            """.trimIndent(),
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    $localFlags
                }
            }
            """.trimIndent(),
        )
        return dir
    }

    private fun checkDiscardFile(projectDir: File): File = projectDir.resolve("build/featured/proguard-featured-checkdiscard.pro")

    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
