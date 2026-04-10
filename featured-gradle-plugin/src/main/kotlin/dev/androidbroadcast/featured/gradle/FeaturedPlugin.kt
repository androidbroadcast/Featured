package dev.androidbroadcast.featured.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

internal const val RESOLVE_FLAGS_TASK_NAME = "resolveFeatureFlags"
internal const val SCAN_ALL_TASK_NAME = "scanAllLocalFlags"
internal const val GENERATE_FLAG_REGISTRAR_TASK_NAME = "generateFlagRegistrar"
internal const val GENERATE_PROGUARD_TASK_NAME = "generateProguardRules"
internal const val GENERATE_IOS_CONST_VAL_TASK_NAME = "generateIosConstVal"
internal const val GENERATE_XCCONFIG_TASK_NAME = "generateXcconfig"
internal const val GENERATE_CONFIG_PARAM_TASK_NAME = "generateConfigParam"

/**
 * Gradle plugin (`dev.androidbroadcast.featured`) that:
 * 1. Exposes the `featured { }` DSL extension for declaring local and remote feature flags.
 * 2. Generates typed `ConfigParam` objects and ergonomic `ConfigValues` extension functions.
 * 3. Generates per-function R8 `-assumevalues` rules for local flags (dead-code elimination).
 * 4. Generates a `GeneratedFlagRegistrar` that registers all flags with `FlagRegistry`.
 * 5. Generates iOS constant-value files and xcconfig for Swift dead-code elimination.
 *
 * Usage in `build.gradle.kts`:
 * ```kotlin
 * plugins { id("dev.androidbroadcast.featured") }
 *
 * featured {
 *     localFlags {
 *         boolean("dark_mode", default = false) { category = "UI" }
 *     }
 *     remoteFlags {
 *         boolean("promo_banner", default = false) { description = "Show promo banner" }
 *     }
 * }
 * ```
 */
public class FeaturedPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("featured", FeaturedExtension::class.java)
        val resolveTask = registerResolveFlagsTask(target)

        // Wire DSL descriptors after the build script runs so the featured { } block is evaluated first.
        // Calling afterEvaluate here (not inside the task config block) avoids IllegalMutationException
        // when tasks are realized after project evaluation (e.g. in ProjectBuilder-based unit tests).
        target.afterEvaluate {
            resolveTask.configure { task ->
                task.localFlagDescriptors.set(extension.localFlags.toDescriptors())
                task.remoteFlagDescriptors.set(extension.remoteFlags.toDescriptors())
            }
        }

        registerConfigParamTask(target, resolveTask)
        registerFlagRegistrarTask(target, resolveTask)
        val proguardTask = registerProguardTask(target, resolveTask)
        registerIosConstValTask(target, resolveTask)
        registerXcconfigTask(target, resolveTask)
        wireToRootAggregator(target, resolveTask)
        listOf("com.android.application", "com.android.library").forEach { pluginId ->
            target.plugins.withId(pluginId) {
                wireProguardToVariants(target, proguardTask)
            }
        }
    }

    private fun registerResolveFlagsTask(target: Project): TaskProvider<ResolveFlagsTask> =
        target.tasks.register(RESOLVE_FLAGS_TASK_NAME, ResolveFlagsTask::class.java) { task ->
            task.group = "featured"
            task.description = "Resolves feature flags declared in the featured { } DSL for '${target.path}'."
            task.moduleName.set(target.path)
            task.outputFile.set(target.layout.buildDirectory.file("featured/flags.txt"))
        }

    private fun registerConfigParamTask(
        target: Project,
        resolveTask: TaskProvider<ResolveFlagsTask>,
    ) {
        target.tasks.register(GENERATE_CONFIG_PARAM_TASK_NAME, GenerateConfigParamTask::class.java) { task ->
            task.group = "featured"
            task.description =
                "Generates ConfigParam objects and ConfigValues extension functions for '${target.path}'."
            task.flagsFile.set(resolveTask.flatMap { it.outputFile })
            task.modulePath.set(target.path)
            task.outputDir.set(target.layout.buildDirectory.dir("generated/featured/commonMain"))
            task.dependsOn(resolveTask)
        }
    }

    private fun registerFlagRegistrarTask(
        target: Project,
        resolveTask: TaskProvider<ResolveFlagsTask>,
    ) {
        target.tasks.register(GENERATE_FLAG_REGISTRAR_TASK_NAME, GenerateFlagRegistrarTask::class.java) { task ->
            task.group = "featured"
            task.description = "Generates GeneratedFlagRegistrar.kt for '${target.path}'."
            task.scanResultFile.set(resolveTask.flatMap { it.outputFile })
            task.packageName.set("dev.androidbroadcast.featured.generated")
            task.outputFile.set(
                target.layout.buildDirectory.file("generated/featured/GeneratedFlagRegistrar.kt"),
            )
            task.dependsOn(resolveTask)
        }
    }

    private fun registerProguardTask(
        target: Project,
        resolveTask: TaskProvider<ResolveFlagsTask>,
    ): TaskProvider<GenerateProguardRulesTask> =
        target.tasks.register(GENERATE_PROGUARD_TASK_NAME, GenerateProguardRulesTask::class.java) { task ->
            task.group = "featured"
            task.description = "Generates ProGuard/R8 -assumevalues rules for local flags in '${target.path}'."
            task.scanResultFile.set(resolveTask.flatMap { it.outputFile })
            task.modulePath.set(target.path)
            task.outputFile.set(target.layout.buildDirectory.file("featured/proguard-featured.pro"))
            task.dependsOn(resolveTask)
        }

    private fun registerIosConstValTask(
        target: Project,
        resolveTask: TaskProvider<ResolveFlagsTask>,
    ) {
        target.tasks.register(GENERATE_IOS_CONST_VAL_TASK_NAME, GenerateIosConstValTask::class.java) { task ->
            task.group = "featured"
            task.description = "Generates iOS const val declarations for local flags in '${target.path}'."
            task.scanResultFile.set(resolveTask.flatMap { it.outputFile })
            task.iosMainOutputFile.set(
                target.layout.buildDirectory.file("generated/featured/iosMain/FeatureFlagOverrides.kt"),
            )
            task.commonMainOutputFile.set(
                target.layout.buildDirectory.file("generated/featured/commonMain/FeatureFlagExpect.kt"),
            )
            task.dependsOn(resolveTask)
        }
    }

    private fun registerXcconfigTask(
        target: Project,
        resolveTask: TaskProvider<ResolveFlagsTask>,
    ) {
        target.tasks.register(GENERATE_XCCONFIG_TASK_NAME, GenerateXcconfigTask::class.java) { task ->
            task.group = "featured"
            task.description = "Generates FeatureFlags.generated.xcconfig for iOS in '${target.path}'."
            task.scanResultFile.set(resolveTask.flatMap { it.outputFile })
            task.outputFile.set(target.layout.buildDirectory.file("featured/FeatureFlags.generated.xcconfig"))
            task.dependsOn(resolveTask)
        }
    }

    /**
     * Ensures the root project has a `scanAllLocalFlags` aggregation task and wires
     * [resolveTask] into it. `./gradlew scanAllLocalFlags` triggers flag resolution
     * across every module that applies the plugin.
     */
    private fun wireToRootAggregator(
        target: Project,
        resolveTask: TaskProvider<ResolveFlagsTask>,
    ) {
        val root = target.rootProject
        if (root.tasks.findByName(SCAN_ALL_TASK_NAME) == null) {
            root.tasks.register(SCAN_ALL_TASK_NAME) { task ->
                task.group = "featured"
                task.description = "Resolves feature flags across all modules applying the Featured plugin."
            }
        }
        root.tasks.named(SCAN_ALL_TASK_NAME) { it.dependsOn(resolveTask) }
    }
}
