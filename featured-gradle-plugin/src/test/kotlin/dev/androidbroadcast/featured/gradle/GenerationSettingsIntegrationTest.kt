package dev.androidbroadcast.featured.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end TestKit coverage for the `generation { }` DSL on a minimal JVM fixture
 * (no AGP / `ANDROID_HOME` required): custom package, class name, and visibility must
 * flow into the generated sources and the ProGuard `-assumevalues` class name, and the
 * new task inputs must participate in up-to-date checks.
 */
class GenerationSettingsIntegrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("project")
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "generation-settings-test"
            """.trimIndent(),
        )
        writeBuildFile(packageName = "com.example.flags")
    }

    private fun writeBuildFile(packageName: String) {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import dev.androidbroadcast.featured.gradle.FeaturedVisibility

            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                generation {
                    packageName = "$packageName"
                }
                localFlags {
                    generation {
                        className = "CheckoutLocalFlags"
                        visibility = FeaturedVisibility.PUBLIC
                    }
                    boolean("dark_mode", default = false)
                }
                remoteFlags {
                    boolean("promo_banner", default = false)
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `generated sources and proguard rules honour the generation settings`() {
        val result =
            gradleRunner()
                .withArguments(GENERATE_CONFIG_PARAM_TASK_NAME, GENERATE_PROGUARD_TASK_NAME, "--stacktrace")
                .build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":$GENERATE_CONFIG_PARAM_TASK_NAME")?.outcome)

        val outputDir = projectDir.resolve("build/generated/featured/commonMain")

        // Local object: custom name (file follows), top-level package, public visibility.
        val localObject = outputDir.resolve("CheckoutLocalFlags.kt")
        assertTrue(localObject.exists(), "Expected CheckoutLocalFlags.kt, found: ${outputDir.list()?.toList()}")
        val localObjectText = localObject.readText()
        assertContains(localObjectText, "package com.example.flags")
        assertContains(localObjectText, "public object CheckoutLocalFlags {")
        assertContains(localObjectText, "public val darkMode: ConfigParam<Boolean> =")

        // Remote object: defaults apart from the top-level package.
        val remoteObject = outputDir.resolve("GeneratedRemoteFlagsRoot.kt")
        assertTrue(remoteObject.exists(), "Expected default-named remote object, found: ${outputDir.list()?.toList()}")
        val remoteObjectText = remoteObject.readText()
        assertContains(remoteObjectText, "package com.example.flags")
        assertContains(remoteObjectText, "internal object GeneratedRemoteFlagsRoot {")

        // Extensions: split per section, visibility follows the section.
        val localExtensions = outputDir.resolve("GeneratedLocalFlagExtensionsRoot.kt")
        assertTrue(localExtensions.exists(), "Expected local extensions file, found: ${outputDir.list()?.toList()}")
        val localExtensionsText = localExtensions.readText()
        assertContains(localExtensionsText, "package com.example.flags")
        assertContains(localExtensionsText, "public fun ConfigValues.isDarkModeEnabled(): Boolean")
        assertContains(localExtensionsText, "getValueCached(CheckoutLocalFlags.darkMode).value")

        val remoteExtensionsText = outputDir.resolve("GeneratedRemoteFlagExtensionsRoot.kt").readText()
        assertContains(remoteExtensionsText, "internal fun ConfigValues.getPromoBanner(): ConfigValue<Boolean>")

        // ProGuard rules must follow the local section's package for R8 DCE to keep working.
        val rules = projectDir.resolve("build/featured/proguard-featured.pro").readText()
        assertContains(rules, "-assumevalues class com.example.flags.GeneratedLocalFlagExtensionsRootKt {")
    }

    @Test
    fun `unchanged settings are up-to-date and a package change re-executes the task`() {
        gradleRunner().withArguments(GENERATE_CONFIG_PARAM_TASK_NAME).build()

        val secondRun = gradleRunner().withArguments(GENERATE_CONFIG_PARAM_TASK_NAME).build()
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            secondRun.task(":$GENERATE_CONFIG_PARAM_TASK_NAME")?.outcome,
            "Unchanged generation settings must keep the task up-to-date.\n${secondRun.output}",
        )

        writeBuildFile(packageName = "com.example.other")
        val thirdRun = gradleRunner().withArguments(GENERATE_CONFIG_PARAM_TASK_NAME).build()
        assertEquals(
            TaskOutcome.SUCCESS,
            thirdRun.task(":$GENERATE_CONFIG_PARAM_TASK_NAME")?.outcome,
            "Changing generation.packageName must re-execute the task.\n${thirdRun.output}",
        )
        assertContains(
            projectDir.resolve("build/generated/featured/commonMain/CheckoutLocalFlags.kt").readText(),
            "package com.example.other",
        )
    }

    @Test
    fun `invalid package name fails the build naming the DSL property`() {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-library")
                id("dev.androidbroadcast.featured")
            }
            featured {
                localFlags {
                    generation { packageName = "1bad.package" }
                    boolean("dark_mode", default = false)
                }
            }
            """.trimIndent(),
        )
        val result = gradleRunner().withArguments(GENERATE_CONFIG_PARAM_TASK_NAME).buildAndFail()
        assertContains(
            result.output,
            "featured.localFlags.generation.packageName '1bad.package' is not a valid Kotlin package name.",
        )
        assertFalse(
            projectDir.resolve("build/generated/featured/commonMain").exists(),
            "No sources must be generated when validation fails",
        )
    }

    private fun gradleRunner(): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
}
