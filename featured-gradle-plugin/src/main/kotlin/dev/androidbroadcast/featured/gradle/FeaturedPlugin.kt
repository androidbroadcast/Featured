package dev.androidbroadcast.featured.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskProvider

private val logger = Logging.getLogger("dev.androidbroadcast.featured")

/** Name of the per-module scan task registered by this plugin. */
internal const val SCAN_TASK_NAME = "scanLocalFlags"

/** Name of the root-level aggregation task that depends on all module scan tasks. */
internal const val SCAN_ALL_TASK_NAME = "scanAllLocalFlags"

/** Name of the per-module ProGuard rules generation task registered by this plugin. */
internal const val GENERATE_PROGUARD_TASK_NAME = "generateProguardRules"

public class FeaturedPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val scanTask = registerModuleScanTask(target)
        registerProguardGenerationTask(target, scanTask)
        wireModuleTaskToRootAggregator(target, scanTask)
    }

    private fun registerModuleScanTask(target: Project): TaskProvider<ScanLocalFlagsTask> =
        target.tasks.register(SCAN_TASK_NAME, ScanLocalFlagsTask::class.java) { task ->
            task.group = "featured"
            task.description =
                "Scans Kotlin sources in '${target.path}' for @LocalFlag-annotated ConfigParam declarations."
            task.moduleName.set(target.path)
            task.outputFile.set(
                target.layout.buildDirectory.file("featured/local-flags.txt"),
            )
            // Wire source files lazily: resolved after all plugins have applied so
            // KMP/Android source sets are fully configured.
            task.sourceFiles.setFrom(
                target.provider { target.kotlinSourceFiles() },
            )
        }

    private fun registerProguardGenerationTask(
        target: Project,
        scanTask: TaskProvider<ScanLocalFlagsTask>,
    ) {
        target.tasks.register(GENERATE_PROGUARD_TASK_NAME, GenerateProguardRulesTask::class.java) { task ->
            task.group = "featured"
            task.description =
                "Generates ProGuard/R8 -assumevalues rules for @LocalFlag(defaultValue=false) flags in '${target.path}'."
            task.scanResultFile.set(scanTask.flatMap { it.outputFile })
            task.outputFile.set(
                target.layout.buildDirectory.file("featured/proguard-featured.pro"),
            )
            task.dependsOn(scanTask)
        }
    }

    /**
     * Ensures the root project has a `scanAllLocalFlags` aggregation task and wires
     * [scanTask] into it as a dependency. This makes `./gradlew scanAllLocalFlags`
     * discover flags across every module that applies the plugin.
     */
    private fun wireModuleTaskToRootAggregator(
        target: Project,
        scanTask: TaskProvider<ScanLocalFlagsTask>,
    ) {
        val root = target.rootProject

        // Register the aggregation task if this is the first module to apply the plugin.
        if (root.tasks.findByName(SCAN_ALL_TASK_NAME) == null) {
            root.tasks.register(SCAN_ALL_TASK_NAME) { task ->
                task.group = "featured"
                task.description =
                    "Aggregates @LocalFlag scan results from all modules applying the Featured plugin."
            }
        }

        // Wire this module's scan task as a dependency of the root aggregator.
        root.tasks.named(SCAN_ALL_TASK_NAME) { it.dependsOn(scanTask) }
    }
}

/**
 * Returns a [FileTree] containing all `.kt` source files on [this] project.
 *
 * Supports Kotlin Multiplatform (`src/<sourceSet>/kotlin`) and conventional JVM
 * (`src/main/kotlin`, `src/main/java`) layouts by scanning the `src/` directory.
 * Custom source directories registered via the Kotlin extension are also included.
 */
internal fun Project.kotlinSourceFiles(): FileTree {
    val trees = mutableListOf<FileTree>()

    // Scan the standard KMP/Kotlin source directory layout under `src/`.
    val srcDir = file("src")
    if (srcDir.isDirectory) {
        trees += fileTree(srcDir) { it.include("**/*.kt") }
    }

    // Also honour custom source directories registered via the Kotlin extension
    // (covers source sets outside the conventional `src/` tree).
    extensions.findByName("kotlin")?.let { ext ->
        runCatching {
            // KotlinProjectExtension exposes getSourceSets() returning a NamedDomainObjectContainer.
            val sourceSets =
                ext::class.java.getMethod("getSourceSets").invoke(ext)
                    as? Iterable<*> ?: return@runCatching
            sourceSets.forEach { ss ->
                ss ?: return@forEach
                runCatching {
                    val kotlin = ss::class.java.getMethod("getKotlin").invoke(ss)
                    val srcDirs =
                        kotlin?.let {
                            it::class.java.getMethod("getSrcDirs").invoke(it) as? Set<*>
                        } ?: return@runCatching
                    srcDirs.filterIsInstance<java.io.File>().forEach { dir ->
                        // Only add dirs outside `src/` to avoid duplicates with the tree above.
                        if (dir.isDirectory && !dir.startsWith(srcDir)) {
                            trees += fileTree(dir) { it.include("**/*.kt") }
                        }
                    }
                }.onFailure { ex ->
                    logger.warn(
                        "[featured] Could not read Kotlin source dirs for source set in '$path': ${ex.message}",
                    )
                }
            }
        }.onFailure { ex ->
            logger.warn(
                "[featured] Could not inspect 'kotlin' extension on project '$path': ${ex.message}",
            )
        }
    }

    return if (trees.isEmpty()) {
        files().asFileTree
    } else {
        trees.reduce { acc, tree -> acc + tree }
    }
}
