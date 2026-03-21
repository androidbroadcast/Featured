package dev.androidbroadcast.featured.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree

public class FeaturedPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val scanTask = target.tasks.register("scanLocalFlags", ScanLocalFlagsTask::class.java) { task ->
            task.group = "featured"
            task.description = "Scans Kotlin sources for @LocalFlag-annotated ConfigParam declarations."
            task.moduleName.set(target.path)
        }

        // Wire Kotlin source files lazily after all plugins have been applied,
        // so that Kotlin/KMP source sets are fully configured before we collect them.
        target.afterEvaluate {
            scanTask.configure { task ->
                task.sourceFiles.setFrom(target.kotlinSourceFiles())
            }
        }
    }
}

/**
 * Returns a [FileTree] containing all `.kt` source files on [this] project.
 *
 * Supports Kotlin Multiplatform (`src/<sourceSet>/kotlin`) and conventional JVM
 * (`src/main/kotlin`, `src/main/java`) layouts, plus any source directories
 * contributed by applied Kotlin plugins via the `sourceSets` extension.
 */
internal fun Project.kotlinSourceFiles(): FileTree {
    val trees = mutableListOf<FileTree>()

    // Scan the standard KMP/Kotlin source directory layout under `src/`.
    val srcDir = file("src")
    if (srcDir.isDirectory) {
        trees += fileTree(srcDir) { it.include("**/*.kt") }
    }

    // Also honour custom source directories registered via the Kotlin or Java
    // `sourceSets` extension (covers custom source sets and non-standard layouts).
    listOf("kotlin", "sourceSets").forEach { extensionName ->
        extensions.findByName(extensionName)?.let { ext ->
            runCatching {
                val sourceSetsMap = ext::class.java.getMethod("getAsMap").invoke(ext)
                    as? Map<*, *> ?: return@runCatching
                sourceSetsMap.values.forEach { ss ->
                    ss ?: return@forEach
                    runCatching {
                        val kotlin = ss::class.java.getMethod("getKotlin").invoke(ss)
                        val srcDirs = kotlin?.let {
                            it::class.java.getMethod("getSrcDirs").invoke(it) as? Set<*>
                        } ?: return@runCatching
                        srcDirs.filterIsInstance<java.io.File>().forEach { dir ->
                            if (dir.isDirectory) trees += fileTree(dir) { it.include("**/*.kt") }
                        }
                    }
                }
            }
        }
    }

    return if (trees.isEmpty()) {
        files().asFileTree
    } else {
        trees.reduce { acc, tree -> acc + tree }
    }
}
