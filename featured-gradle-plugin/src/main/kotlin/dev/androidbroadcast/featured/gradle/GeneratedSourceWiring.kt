package dev.androidbroadcast.featured.gradle

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

/*
 * Wires a directory of plugin-generated Kotlin sources into compilation so consumer modules need
 * zero manual `srcDir` / `dependsOn` boilerplate. Each helper is called lazily — only when the
 * matching plugin is present (`plugins.withId`) — so the three branches never co-fire (AGP 9 makes
 * `com.android.application` / `com.android.library` + `org.jetbrains.kotlin.multiplatform` a hard
 * error, and Android Kotlin uses `org.jetbrains.kotlin.android`, never `.jvm`).
 *
 * KMP / Kotlin-JVM receive the producer task's native @OutputDirectory provider
 * (`generateConfigParam.outputDir`, a `Provider<Directory>` obtained via `flatMap`). Passing it to
 * `SourceDirectorySet.srcDir(Provider)` records the producer as the directory's builder, so Gradle
 * auto-infers the task dependency for EVERY consumer (compile*, sourcesJar, metadata, lint, …) with
 * no name-matched `dependsOn`. AGP cannot take a provider (its source set rejects one at
 * configuration time), so it gets a resolved [File] plus the explicit [dependOnProducer] ordering.
 */

/**
 * Wires [generatedDir] into the KMP `commonMain` source set.
 *
 * `KotlinSourceSet.kotlin` is Gradle's [org.gradle.api.file.SourceDirectorySet], whose
 * `srcDir(Object)` accepts a [Provider]/[Directory]. `com.android.kotlin.multiplatform.library`
 * co-requires `org.jetbrains.kotlin.multiplatform` (`commonMain` exists), so this branch covers it
 * too.
 *
 * Two wiring shapes share this helper:
 * - **Task-carrying provider (per-module `FeaturedPlugin`, [producer] omitted).** [generatedDir] is
 *   the producer task's @OutputDirectory provider (`generateConfigParam.outputDir`, obtained via
 *   `flatMap`). `srcDir(Provider)` records the producer as the directory's builder, so Gradle
 *   auto-infers the task dependency for every consumer — no explicit `dependsOn` needed.
 * - **Pure layout provider (aggregator, [producer] passed).** The registry task is @OutputFile-based;
 *   a derived `.map { it.parentFile }` provider eager-errors inside
 *   `com.android.kotlin.multiplatform.library`. The aggregator therefore passes a plain layout
 *   provider (no task attached) for [generatedDir] and supplies [producer] so ordering is carried by
 *   an explicit `dependsOn` via [dependOnProducer].
 */
internal fun wireGeneratedSourcesToKmp(
    project: Project,
    generatedDir: Provider<out Any>,
    producer: TaskProvider<*>? = null,
) {
    val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kotlin.sourceSets
        .getByName("commonMain")
        .kotlin
        .srcDir(generatedDir)
    if (producer != null) dependOnProducer(project, producer)
}

/**
 * Wires [generatedDir] into the Kotlin/JVM `main` source set.
 *
 * As with [wireGeneratedSourcesToKmp], `KotlinSourceSet.kotlin` is Gradle's
 * [org.gradle.api.file.SourceDirectorySet]. When [generatedDir] is the producer task's
 * @OutputDirectory provider (per-module `FeaturedPlugin`, [producer] omitted) the task dependency is
 * auto-inferred for every consumer. When [generatedDir] is a pure layout provider (aggregator,
 * because the registry task is @OutputFile-based and a derived provider eager-errors), [producer] is
 * passed so ordering is carried by an explicit `dependsOn` via [dependOnProducer].
 */
internal fun wireGeneratedSourcesToKotlinJvm(
    project: Project,
    generatedDir: Provider<out Any>,
    producer: TaskProvider<*>? = null,
) {
    val kotlin = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
    kotlin.sourceSets
        .getByName("main")
        .kotlin
        .srcDir(generatedDir)
    if (producer != null) dependOnProducer(project, producer)
}

/**
 * Adds an explicit `dependsOn([producer])` to every Kotlin-compile, `ksp*`, and AGP
 * annotation-extraction (`extract*Annotations`) task so the generated sources are produced before
 * any task that consumes the generated source dir. Used by the AGP branch only — its
 * `AndroidSourceDirectorySet` takes a resolved [File] that carries no task dependency. The KMP and
 * Kotlin/JVM branches do not call this: their `srcDir(Provider)` auto-infers the dependency.
 * AGP's `extractDebugAnnotations` / `extractReleaseAnnotations` read the generated source directory
 * and fail validation ("uses output of ':generateConfigParam' without declaring an explicit
 * dependency") if the ordering is not wired here.
 *
 * The Kotlin clause matches `name.contains("Kotlin")`, NOT `endsWith("Kotlin")`: KMP target compile
 * tasks end with the target, not "Kotlin" — `compileKotlinJvm`, `compileDebugKotlinAndroid`,
 * `compileReleaseKotlinAndroid`, `compileCommonMainKotlinMetadata`, `compileKotlinIosX64`, etc. An
 * `endsWith("Kotlin")` check only catches the plain Kotlin/JVM `compileKotlin` and wires nothing for
 * KMP modules, leaving the generated dir un-produced before compile.
 */
private fun dependOnProducer(
    project: Project,
    producer: TaskProvider<*>,
) {
    project.tasks.configureEach { task: Task ->
        if ((task.name.startsWith("compile") && task.name.contains("Kotlin")) ||
            task.name.startsWith("ksp") ||
            (task.name.startsWith("extract") && task.name.endsWith("Annotations"))
        ) {
            task.dependsOn(producer)
        }
    }
}

/**
 * Wires [generatedDirFile] into the plain-AGP `main` Kotlin source set.
 *
 * AGP's `AndroidSourceSet.kotlin` is [com.android.build.api.dsl.AndroidSourceDirectorySet] — NOT
 * Gradle's `SourceDirectorySet`. It REJECTS a [Provider] at configuration time (guard
 * `DISALLOW_PROVIDER_IN_ANDROID_SOURCE_SET`, default-on, hard error in AGP 10), so a resolved
 * [File] path is added instead and the task ordering is wired separately via an explicit
 * `dependsOn` on the Kotlin-compile and KSP tasks. `srcDir(Any)` is `@Deprecated` in AGP 9, so we
 * mutate the `directories` set directly.
 *
 * `addGeneratedSourceDirectory(...)` is deliberately NOT used: it is a per-variant convention, and
 * reusing one global [producer] task across `debug` + `release` would share a single dir and bypass
 * AGP path generation.
 *
 * No-ops on an Android module with no Kotlin source set (a pure-Java AGP module never references the
 * generated objects).
 */
internal fun wireGeneratedSourcesToAndroid(
    project: Project,
    generatedDirFile: File,
    producer: TaskProvider<*>,
) {
    val android = project.extensions.getByType(CommonExtension::class.java)
    val mainSourceSet = android.sourceSets.findByName("main") ?: return
    // Resolved absolute path, not a Provider — the Android source set rejects providers at
    // configuration time. directories is a MutableSet<String> (srcDir(Any) is @Deprecated).
    mainSourceSet.kotlin.directories.add(generatedDirFile.absolutePath)
    // The File path carries no task dependency, so order the producer before the Kotlin-compile
    // and KSP tasks explicitly.
    dependOnProducer(project, producer)
}
