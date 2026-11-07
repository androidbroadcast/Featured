package dev.androidbroadcast.featured.gradle

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifestJson
import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.androidSdkDirOrNull
import dev.androidbroadcast.featured.gradle.manifest.copyManifestFixture
import kotlinx.serialization.decodeFromString
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android application fixtures for build-type defaults. AGP 9.1.0, matching the plugin's
 * test classpath. Skips without an Android SDK; a passing plugin test task is not proof
 * these ran.
 */
class BuildTypeDefaultAndroidTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `each build type compiles its effective default and release rules ignore debug`() {
        assumeAndroidSdk()
        val projectDir = androidApp(successScript())
        projectDir.resolve("src/main/kotlin/com/example/CheckoutVariant.kt").also { it.parentFile.mkdirs() }.writeText(
            """
            package com.example
            enum class CheckoutVariant { LEGACY, OTHER }
            """.trimIndent() + "\n",
        )
        writeProof(projectDir, "debugOnly")

        val debug = run(projectDir, "compileDebugKotlin")
        assertProducer(debug, "debug")
        assertTaskSucceeded(debug, ":compileDebugKotlin")
        assertDefault(projectDir, "debug", "debugOnly", "true")
        assertDefault(projectDir, "debug", "releaseOff", "true")
        assertAccessor(projectDir, "debug")

        val release = run(projectDir, "compileReleaseKotlin")
        assertProducer(release, "release")
        assertTaskSucceeded(release, ":compileReleaseKotlin")
        assertDefault(projectDir, "release", "debugOnly", "false")
        assertDefault(projectDir, "release", "releaseOff", "false")
        assertDefault(projectDir, "release", "checkoutVariant", "com.example.CheckoutVariant.OTHER")
        assertAccessor(projectDir, "release")

        val benchmark = run(projectDir, "compileBenchmarkKotlin")
        assertProducer(benchmark, "benchmark")
        assertTaskSucceeded(benchmark, ":compileBenchmarkKotlin")
        assertDefault(projectDir, "benchmark", "debugOnly", "false")
        assertDefault(projectDir, "benchmark", "releaseOff", "true")

        val base = run(projectDir, GENERATE_CONFIG_PARAM_TASK_NAME)
        assertGeneratedSourceTaskRan(base, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        val commonMain = projectDir.resolve("build/generated/featured/commonMain/GeneratedLocalFlagsRoot.kt").readText()
        assertContains(commonMain, "key = \"debug_only\", defaultValue = false")
        assertFalse(commonMain.contains("key = \"debug_only\", defaultValue = true"))

        val rules = run(projectDir, GENERATE_PROGUARD_TASK_NAME)
        assertGeneratedSourceTaskRan(rules, ":$GENERATE_PROGUARD_TASK_NAME")
        val proguard = projectDir.resolve("build/featured/proguard-featured.pro").readText()
        assertContains(
            proguard,
            "boolean isDebugOnlyEnabled(dev.androidbroadcast.featured.ConfigValues) return false;",
        )
        assertContains(
            proguard,
            "boolean isReleaseOffEnabled(dev.androidbroadcast.featured.ConfigValues) return false;",
        )
        assertContains(
            proguard,
            "boolean isUnchangedEnabled(dev.androidbroadcast.featured.ConfigValues) return false;",
        )
        assertFalse(proguard.contains("isDebugOnlyEnabled(dev.androidbroadcast.featured.ConfigValues) return true;"))
        assertFalse(proguard.contains("isReleaseOffEnabled(dev.androidbroadcast.featured.ConfigValues) return true;"))
        assertFalse(proguard.contains("getCheckoutVariant"))
        val proguardNames =
            projectDir
                .resolve("build/featured")
                .list()
                ?.filter { it.endsWith(".pro") }
                .orEmpty()
                .sorted()
        assertEquals(listOf("proguard-featured.pro"), proguardNames)

        val manifestRun = run(projectDir, "generateFeaturedManifest")
        assertGeneratedSourceTaskRan(manifestRun, ":generateFeaturedManifest")
        val rawManifest = projectDir.resolve("build/featured/featured-manifest.json").readText()
        val manifest = FeaturedManifestJson.decodeFromString<FeaturedManifest>(rawManifest)
        assertEquals(SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals("false", manifest.flags.single { it.key == "debug_only" }.defaultValue)
        assertEquals("true", manifest.flags.single { it.key == "release_off" }.defaultValue)
        assertEquals("LEGACY", manifest.flags.single { it.key == "checkout_variant" }.defaultValue)
        assertFalse(rawManifest.contains("buildType"))
        assertFalse(rawManifest.contains("defaultFor"))

        val ios = run(projectDir, GENERATE_XCCONFIG_TASK_NAME, GENERATE_IOS_CONST_VAL_TASK_NAME)
        assertGeneratedSourceTaskRan(ios, ":$GENERATE_XCCONFIG_TASK_NAME")
        assertGeneratedSourceTaskRan(ios, ":$GENERATE_IOS_CONST_VAL_TASK_NAME")
        val xcconfig = projectDir.resolve("build/featured/FeatureFlags.generated.xcconfig").readText()
        assertContains(xcconfig, "DISABLE_DEBUG_ONLY")
        assertFalse(xcconfig.contains("DISABLE_RELEASE_OFF"))
        val iosConst = projectDir.resolve("build/generated/featured/iosMain/FeatureFlagOverrides.kt").readText()
        assertContains(iosConst, "public actual const val debug_only: Boolean = false")
        assertContains(iosConst, "public actual const val release_off: Boolean = true")
        assertFalse(iosConst.contains("public actual const val release_off: Boolean = false"))

        val checkDiscard = run(projectDir, GENERATE_CHECK_DISCARD_TASK_NAME)
        assertGeneratedSourceTaskRan(checkDiscard, ":$GENERATE_CHECK_DISCARD_TASK_NAME")
        val checkDiscardText = projectDir.resolve("build/featured/proguard-featured-checkdiscard.pro").readText()
        assertContains(checkDiscardText, "-checkdiscard class com.example.Before { *; }")
        assertContains(checkDiscardText, "-checkdiscard class com.example.After { *; }")
    }

    @Test
    fun `discard is rejected when the release effective default is true`() {
        assumeAndroidSdk()
        val projectDir =
            androidApp(
                flags =
                    """
                    boolean("release_true", default = false) {
                        defaultFor("debug", false)
                        defaultFor("release", true)
                        discard("com.example.Feature")
                    }
                    """.trimIndent(),
            )

        val result = fail(projectDir, GENERATE_CHECK_DISCARD_TASK_NAME)
        assertContains(result.output, "release_true")
        val file = projectDir.resolve("build/featured/proguard-featured-checkdiscard.pro")
        assertFalse(file.exists() && file.readText().contains("com.example.Feature"))
    }

    @Test
    fun `unknown build type names fail and name the flag`() {
        assumeAndroidSdk()
        val typo =
            androidApp(
                flags = """boolean("typo_flag", default = false) { defaultFor("typo", true) }""",
            )
        val typoResult = fail(typo, "help")
        assertContains(typoResult.output, "typo_flag")
        assertContains(typoResult.output, "typo")

        val wrongCase =
            androidApp(
                flags = """boolean("case_flag", default = false) { defaultFor("Debug", true) }""",
            )
        val caseResult = fail(wrongCase, "help")
        assertContains(caseResult.output, "case_flag")
        assertContains(caseResult.output, "Debug")
    }

    @Test
    fun `flavor and variant names are not build types`() {
        assumeAndroidSdk()
        val flavor =
            androidApp(
                flags = """boolean("flavor_flag", default = false) { defaultFor("free", true) }""",
                flavors = true,
            )
        val flavorResult = fail(flavor, "help")
        assertContains(flavorResult.output, "flavor_flag")
        assertContains(flavorResult.output, "free")

        val variant =
            androidApp(
                flags = """boolean("variant_flag", default = false) { defaultFor("freeDebug", true) }""",
                flavors = true,
            )
        val variantResult = fail(variant, "help")
        assertContains(variantResult.output, "variant_flag")
        assertContains(variantResult.output, "freeDebug")
    }

    @Test
    fun `flavors of one build type compile the same build-type file`() {
        assumeAndroidSdk()
        val projectDir =
            androidApp(
                flags = """boolean("debug_only", default = false) { defaultFor("debug", true) }""",
                flavors = true,
            )
        writeProof(projectDir, "debugOnly")

        val free = run(projectDir, "compileFreeDebugKotlin")
        assertProducer(free, "debug")
        assertTaskAbsent(free, ":generateConfigParamFreeDebug")
        assertTaskSucceeded(free, ":compileFreeDebugKotlin")

        val paid = run(projectDir, "compilePaidDebugKotlin")
        assertProducer(paid, "debug")
        assertTaskAbsent(paid, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        assertTaskSucceeded(paid, ":compilePaidDebugKotlin")
        assertDefault(projectDir, "debug", "debugOnly", "true")
        assertFalse(
            projectDir.resolve(objectFile("debug")).readText().contains("free"),
            "The shared debug file must not carry a flavor value",
        )
    }

    @Test
    fun `benchmark override is valid when the android block comes after featured`() {
        assumeAndroidSdk()
        val projectDir = androidApp(featuredFirst = true, benchmarkOverride = true)
        writeProof(projectDir, "experiment")
        val result = run(projectDir, "compileBenchmarkKotlin")
        assertProducer(result, "benchmark")
        assertTaskSucceeded(result, ":compileBenchmarkKotlin")
        assertDefault(projectDir, "benchmark", "experiment", "false")
    }

    @Test
    fun `benchmark override is valid when the android block comes first`() {
        assumeAndroidSdk()
        val projectDir = androidApp(featuredFirst = false, benchmarkOverride = true)
        writeProof(projectDir, "experiment")
        val result = run(projectDir, "compileBenchmarkKotlin")
        assertProducer(result, "benchmark")
        assertTaskSucceeded(result, ":compileBenchmarkKotlin")
        assertDefault(projectDir, "benchmark", "experiment", "false")
    }

    private fun successScript(): String =
        """
        boolean("debug_only", default = false) {
            defaultFor("debug", true)
        }
        boolean("release_off", default = true) {
            defaultFor("debug", true)
            defaultFor("release", false)
        }
        boolean("unchanged", default = false)
        enum("checkout_variant", typeFqn = "com.example.CheckoutVariant", default = "LEGACY") {
            defaultFor("release", "OTHER")
        }
        boolean("discard_before", default = true) {
            discard("com.example.Before")
            defaultFor("debug", true)
            defaultFor("release", false)
        }
        boolean("discard_after", default = true) {
            defaultFor("debug", true)
            defaultFor("release", false)
            discard("com.example.After")
        }
        """.trimIndent()

    private fun androidApp(
        flags: String = successScript(),
        flavors: Boolean = false,
        featuredFirst: Boolean = true,
        benchmarkOverride: Boolean = false,
    ): File {
        val dir = tempFolder.newFolder()
        copyManifestFixture("wiring-android-app-project", dir)
        val sdkDir = androidSdkDirOrNull()!!
        dir.resolve("local.properties").writeText("sdk.dir=${sdkDir.invariantSeparatorsPath}\n")
        val flagBlock =
            if (benchmarkOverride) {
                """
                boolean("experiment", default = true) {
                    defaultFor("benchmark", false)
                }
                """.trimIndent()
            } else {
                flags
            }
        val featured =
            """
            featured {
                localFlags {
                    $flagBlock
                }
            }
            """.trimIndent()
        val android =
            """
            android {
                namespace = "dev.androidbroadcast.featured.wiringapp"
                compileSdk = 36
                defaultConfig {
                    minSdk = 24
                    targetSdk = 36
                }
                ${if (flavors) flavorBlock() else ""}
                buildTypes {
                    create("benchmark")
                }
            }
            """.trimIndent()
        val body = if (featuredFirst) "$featured\n$android" else "$android\n$featured"
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "9.1.0"
                id("dev.androidbroadcast.featured")
            }
            $body
            """.trimIndent() + "\n",
        )
        return dir
    }

    private fun flavorBlock(): String =
        """
        flavorDimensions += "tier"
        productFlavors {
            create("free") { dimension = "tier" }
            create("paid") { dimension = "tier" }
        }
        """.trimIndent()

    private fun writeProof(
        projectDir: File,
        property: String,
    ) {
        projectDir.resolve("src/main/kotlin/WiringProof.kt").writeText(
            """
            import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsRoot

            // Compiles only if the variant producer put this object on the compilation.
            internal val proof: String = GeneratedLocalFlagsRoot.$property.key
            """.trimIndent() + "\n",
        )
    }

    private fun objectFile(buildType: String): String = "build/generated/featured/buildType/$buildType/GeneratedLocalFlagsRoot.kt"

    private fun assertDefault(
        projectDir: File,
        buildType: String,
        property: String,
        literal: String,
    ) {
        val source = projectDir.resolve(objectFile(buildType)).readText()
        val declaration = source.lines().single { "val $property =" in it || "val $property:" in it }
        assertContains(declaration, "defaultValue = $literal")
    }

    private fun assertAccessor(
        projectDir: File,
        buildType: String,
    ) {
        val extensions =
            projectDir
                .resolve("build/generated/featured/buildType/$buildType/GeneratedLocalFlagExtensionsRoot.kt")
                .readText()
        assertContains(extensions, "fun ConfigValues.isDebugOnlyEnabled(): Boolean")
        assertFalse(extensions.contains("): ConfigValue<"))
        assertContains(projectDir.resolve(objectFile(buildType)).readText(), "FlagScope.LOCAL")
    }

    private fun assertProducer(
        result: BuildResult,
        buildType: String,
    ) {
        assertGeneratedSourceTaskRan(result, ":${BuildTypeDefaults.taskName(buildType)}")
        assertTaskAbsent(result, ":$GENERATE_CONFIG_PARAM_TASK_NAME")
        if (buildType != "release") {
            assertTaskAbsent(result, ":${BuildTypeDefaults.taskName("release")}")
        }
        if (buildType != "debug") {
            assertTaskAbsent(result, ":${BuildTypeDefaults.taskName("debug")}")
        }
    }

    private fun assertGeneratedSourceTaskRan(
        result: BuildResult,
        taskPath: String,
    ) {
        val outcome = result.task(taskPath)?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE,
            "Expected $taskPath to run (got $outcome).\n${result.output}",
        )
    }

    private fun assertTaskAbsent(
        result: BuildResult,
        taskPath: String,
    ) {
        assertTrue(
            result.task(taskPath) == null,
            "Expected $taskPath to be absent (got ${result.task(taskPath)?.outcome}).\n${result.output}",
        )
    }

    private fun assertTaskSucceeded(
        result: BuildResult,
        taskPath: String,
    ) {
        val outcome = result.task(taskPath)?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE,
            "Expected $taskPath to succeed (got $outcome).\n${result.output}",
        )
    }

    private fun run(
        projectDir: File,
        vararg tasks: String,
    ): BuildResult = runner(projectDir, *tasks).build()

    private fun fail(
        projectDir: File,
        vararg tasks: String,
    ): BuildResult = runner(projectDir, *tasks).buildAndFail()

    private fun runner(
        projectDir: File,
        vararg tasks: String,
    ): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()
            .withArguments(*tasks, "--stacktrace", "--no-build-cache")

    private fun assumeAndroidSdk() {
        assumeTrue(
            "ANDROID_HOME or ANDROID_SDK_ROOT must be set to run Android build-type default tests",
            androidSdkDirOrNull() != null,
        )
    }
}
