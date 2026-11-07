package dev.androidbroadcast.featured.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.nio.file.Path

/**
 * Wires one [GenerateConfigParamTask] per Android build type into every variant of that
 * build type.
 *
 * `addGeneratedSourceDirectory` relocates one task's output per variant and cannot be shared
 * by `freeDebug` and `paidDebug`. [addStaticSourceDirectory][com.android.build.api.variant.SourceDirectories.addStaticSourceDirectory]
 * adds one directory, resolved relative to the project directory, to every variant of the
 * build type. It does not record a task dependency, so the variant's `compile*Kotlin`,
 * `ksp*`, and `extract*Annotations` tasks depend on that build-type producer explicitly.
 * `extract*Annotations` reads the static Kotlin directory and fails validation when it uses
 * the producer output without `dependsOn`.
 *
 * The shared `generateConfigParam` task is not a dependency of those variant tasks. Its
 * `commonMain` output stays the base default for JVM and KMP.
 */
internal fun wireApplicationBuildTypeConfigParams(
    project: Project,
    extension: FeaturedExtension,
    registerBuildTypeTask: (buildType: String) -> TaskProvider<GenerateConfigParamTask>,
) {
    val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    wireBuildTypeConfigParams(
        project = project,
        extension = extension,
        registerBuildTypeTask = registerBuildTypeTask,
        finalizeBuildTypes = { action -> components.finalizeDsl { action(it.buildTypes.map { type -> type.name }.toSet()) } },
        onVariants = { action -> components.onVariants { action(it) } },
    )
}

internal fun wireLibraryBuildTypeConfigParams(
    project: Project,
    extension: FeaturedExtension,
    registerBuildTypeTask: (buildType: String) -> TaskProvider<GenerateConfigParamTask>,
) {
    val components = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
    wireBuildTypeConfigParams(
        project = project,
        extension = extension,
        registerBuildTypeTask = registerBuildTypeTask,
        finalizeBuildTypes = { action -> components.finalizeDsl { action(it.buildTypes.map { type -> type.name }.toSet()) } },
        onVariants = { action -> components.onVariants { action(it) } },
    )
}

internal fun Project.buildTypeConfigParamDirectory(buildType: String): Provider<Directory> =
    layout.buildDirectory.dir("generated/featured/buildType/$buildType")

/**
 * Path [com.android.build.api.variant.SourceDirectories.addStaticSourceDirectory] resolves
 * relative to the project directory. Matches [buildTypeConfigParamDirectory].
 */
internal fun Project.buildTypeConfigParamRelativePath(buildType: String): String {
    val output = buildTypeConfigParamDirectory(buildType).get().asFile.toPath()
    val projectDir = layout.projectDirectory.asFile.toPath()
    return projectDir.relativize(output).toPortablePath()
}

private fun wireBuildTypeConfigParams(
    project: Project,
    extension: FeaturedExtension,
    registerBuildTypeTask: (buildType: String) -> TaskProvider<GenerateConfigParamTask>,
    finalizeBuildTypes: ((Set<String>) -> Unit) -> Unit,
    onVariants: ((Variant) -> Unit) -> Unit,
) {
    val producers = linkedMapOf<String, TaskProvider<GenerateConfigParamTask>>()
    val wirings = mutableListOf<VariantSourceWiring>()

    // Registered before AGP creates compile tasks. The action reads [wirings], which
    // onVariants fills before those tasks exist. Do not capture Project here.
    project.tasks.configureEach { task ->
        val variantName = matchingVariantName(task.name, wirings.map { it.variantName }) ?: return@configureEach
        val producer = wirings.first { it.variantName == variantName }.producer
        task.dependsOn(producer)
    }

    finalizeBuildTypes { buildTypeNames ->
        // Discard is not nested under the name check: both errors are reported together.
        BuildTypeDefaultValidation.failIf(
            BuildTypeDefaultValidation.discardErrors(extension.localFlags.flags) +
                BuildTypeDefaultValidation.unknownBuildTypeErrors(extension.localFlags.flags, buildTypeNames),
        )
        for (buildType in buildTypeNames) {
            producers[buildType] = registerBuildTypeTask(buildType)
        }
    }

    onVariants { variant ->
        val buildType =
            variant.buildType
                ?: throw GradleException(
                    "Android variant \"${variant.name}\" has no build type, so Featured cannot wire a " +
                        "build-type flag default.",
                )
        val producer =
            producers[buildType]
                ?: throw GradleException(
                    "Featured has no ConfigParam producer for Android build type \"$buildType\" " +
                        "(variant \"${variant.name}\").",
                )
        val kotlinSources =
            variant.sources.kotlin
                ?: throw GradleException(
                    "Android variant \"${variant.name}\" has no Kotlin sources, so Featured cannot wire " +
                        "build-type flag defaults.",
                )
        kotlinSources.addStaticSourceDirectory(project.buildTypeConfigParamRelativePath(buildType))
        wirings += VariantSourceWiring(variant.name, producer)
    }
}

internal data class VariantSourceWiring(
    val variantName: String,
    val producer: TaskProvider<GenerateConfigParamTask>,
)

/**
 * Variant whose capitalized name is contained in [taskName], preferring the longest name so
 * `FreeDebug` wins over `Debug`. Returns null when [taskName] is not a ConfigParam consumer.
 */
internal fun matchingVariantName(
    taskName: String,
    variantNames: Collection<String>,
): String? {
    if (!isBuildTypeConfigParamConsumer(taskName)) return null
    return variantNames
        .filter { name -> name.isNotEmpty() && taskName.contains(name.agpCapitalized()) }
        .maxByOrNull { it.length }
}

internal fun isBuildTypeConfigParamConsumer(taskName: String): Boolean =
    (taskName.startsWith("compile") && taskName.contains("Kotlin")) ||
        taskName.startsWith("ksp") ||
        (taskName.startsWith("extract") && taskName.endsWith("Annotations"))

private fun Path.toPortablePath(): String = toString().replace('\\', '/')
