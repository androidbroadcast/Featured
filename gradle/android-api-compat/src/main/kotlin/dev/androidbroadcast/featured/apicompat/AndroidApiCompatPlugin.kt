package dev.androidbroadcast.featured.apicompat

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Registers the Android dump-and-check. Does not enable kotlin.abiValidation and does not
 * depend on internalDumpKotlinAbi, so JVM and klib dumps are not written.
 */
public class AndroidApiCompatPlugin : Plugin<Project> {
    public override fun apply(project: Project) {
        when (val module = AndroidApiGate.moduleFor(project.path)) {
            null -> {
                if (project != project.rootProject) {
                    throw GradleException(
                        "${project.path} is not in the Android API compatibility gate. Do not apply $ID here.",
                    )
                }
                registerAggregates(project)
            }

            else -> {
                registerModule(project, module)
            }
        }
    }

    private fun registerAggregates(project: Project) {
        project.tasks.register(CHECK_TASK) {
            it.group = VERIFICATION
            it.description =
                "Fails if a gated Android public signature differs from its committed baseline."
            it.dependsOn(AndroidApiGate.modules.map { module -> "${module.projectPath}:$CHECK_TASK" })
        }
        project.tasks.register(UPDATE_TASK) {
            it.group = VERIFICATION
            it.description =
                "Writes the ten Android API baselines. Not a dependency of check or CI."
            it.dependsOn(AndroidApiGate.modules.map { module -> "${module.projectPath}:$UPDATE_TASK" })
        }
    }

    private fun registerModule(
        project: Project,
        module: AndroidApiModule,
    ) {
        val dump = registerTasks(project, module)
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            wireKmp(project, dump)
        }
        project.pluginManager.withPlugin("com.android.library") {
            wireAndroidLibrary(project, dump)
        }
    }

    private fun registerTasks(
        project: Project,
        module: AndroidApiModule,
    ): TaskProvider<DumpAndroidApiTask> {
        val tools = toolsConfiguration(project)
        val baseline = project.layout.projectDirectory.file("api/android/${module.baselineFileName}")
        val dump =
            project.tasks.register(DUMP_TASK, DumpAndroidApiTask::class.java) { task ->
                task.modulePath.set(module.projectPath)
                task.toolsClasspath.from(tools)
                task.classJars.convention(emptyList())
                task.classDirectories.convention(emptyList())
                task.dumpFile.set(project.layout.buildDirectory.file("android-api/${module.baselineFileName}"))
            }
        val update =
            project.tasks.register(UPDATE_TASK, UpdateAndroidApiTask::class.java) { task ->
                task.group = VERIFICATION
                task.description =
                    "Writes ${module.baselinePath} from the Android dumper. Not a dependency of check or CI."
                task.modulePath.set(module.projectPath)
                task.generatedDump.set(dump.flatMap { it.dumpFile })
                task.baseline.set(baseline)
                task.dependsOn(dump)
            }
        project.tasks.register(CHECK_TASK, CheckAndroidApiTask::class.java) { task ->
            task.group = VERIFICATION
            task.description =
                "Fails if ${module.projectPath} Android public signatures differ from ${module.baselinePath}."
            task.modulePath.set(module.projectPath)
            task.generatedDump.set(dump.flatMap { it.dumpFile })
            task.baseline.set(baseline)
            task.dependsOn(dump)
            // Ordering only. mustRunAfter does not pull update into check or CI.
            task.mustRunAfter(update)
        }
        return dump
    }

    private fun wireKmp(
        project: Project,
        dump: TaskProvider<DumpAndroidApiTask>,
    ) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.targets.configureEach { target ->
            if (target.name != "android") {
                return@configureEach
            }
            target.compilations.configureEach { compilation ->
                if (compilation.name != KotlinCompilation.MAIN_COMPILATION_NAME) {
                    return@configureEach
                }
                dump.configure {
                    it.kmpClasses.from(compilation.output.classesDirs)
                    it.dependsOn(compilation.compileTaskProvider)
                }
            }
        }
    }

    private fun wireAndroidLibrary(
        project: Project,
        dump: TaskProvider<DumpAndroidApiTask>,
    ) {
        val androidComponents = project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
        androidComponents.onVariants(androidComponents.selector().withBuildType(RELEASE_BUILD_TYPE)) { variant ->
            check(variant.buildType == RELEASE_BUILD_TYPE) {
                "${project.path} Android API gate expected the release variant, found ${variant.name}."
            }
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .use(dump)
                .toGet(
                    ScopedArtifact.CLASSES,
                    DumpAndroidApiTask::classJars,
                    DumpAndroidApiTask::classDirectories,
                )
            dump.configure {
                it.dependsOn("compileReleaseKotlin", "compileReleaseJavaWithJavac")
            }
        }
    }

    private fun toolsConfiguration(project: Project): Configuration {
        project.configurations.findByName(TOOLS_CONFIGURATION)?.let { return it }
        val configuration =
            project.configurations.create(TOOLS_CONFIGURATION) {
                it.isCanBeResolved = true
                it.isCanBeConsumed = false
                it.isTransitive = true
            }
        val version =
            project.extensions
                .getByType(VersionCatalogsExtension::class.java)
                .named("libs")
                .findVersion("kotlin")
                .orElseThrow { GradleException("libs catalog is missing the kotlin version") }
                .requiredVersion
        project.dependencies.add(TOOLS_CONFIGURATION, "org.jetbrains.kotlin:kotlin-build-tools-impl:$version")
        return configuration
    }

    public companion object {
        public const val ID: String = "dev.androidbroadcast.featured.internal.android-api-compat"
        internal const val CHECK_TASK: String = "checkAndroidApi"
        internal const val UPDATE_TASK: String = "updateAndroidApi"
        private const val DUMP_TASK: String = "dumpAndroidApi"
        private const val TOOLS_CONFIGURATION: String = "androidApiCompatTools"
        private const val VERIFICATION: String = "verification"
        private const val RELEASE_BUILD_TYPE: String = "release"
    }
}
