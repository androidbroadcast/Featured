package dev.androidbroadcast.featured.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Wires the generated ProGuard rules files into every application variant via the AGP Variant API.
 *
 * Called lazily — only when `com.android.application` is present on the project.
 *
 * Both the `-assumevalues` rules ([proguardTask]) and the `-checkdiscard` rules
 * ([checkDiscardTask]) are added: the first drives flag dead-code elimination, the second
 * verifies it in the same R8 run. The check-discard file is empty unless a flag declares
 * `discard(...)`, and an empty ProGuard configuration is a harmless no-op.
 *
 * AGP 9.x does not propagate implicit Gradle task dependencies through [Variant.proguardFiles],
 * so both tasks are also wired explicitly as dependencies of every `minify*WithR8` task.
 */
internal fun wireProguardToApplicationVariants(
    project: Project,
    proguardTask: TaskProvider<GenerateProguardRulesTask>,
    checkDiscardTask: TaskProvider<GenerateCheckDiscardRulesTask>,
) {
    val androidComponents =
        project.extensions
            .getByType(AndroidComponentsExtension::class.java)
    androidComponents.onVariants { variant ->
        variant.proguardFiles.add(
            proguardTask.flatMap { it.outputFile },
        )
        variant.proguardFiles.add(
            checkDiscardTask.flatMap { it.outputFile },
        )
    }
    // AGP 9.x does not propagate implicit task dependencies through variant.proguardFiles,
    // so we wire an explicit dependsOn on every R8 minify task.
    project.tasks.configureEach { task ->
        if (task.name.startsWith("minify") && task.name.endsWith("WithR8")) {
            task.dependsOn(proguardTask)
            task.dependsOn(checkDiscardTask)
        }
    }
}

/**
 * Wires the generated ProGuard rules files as consumer ProGuard rules for every library variant.
 *
 * Called lazily — only when `com.android.library` is present on the project.
 *
 * Library modules do not run R8 themselves, so [Variant.proguardFiles] would never be applied.
 * Consumer ProGuard rules are bundled into the AAR and forwarded to every consuming app's R8,
 * which is where the `-assumevalues` rules need to run for flag dead-code elimination to work.
 * The `-checkdiscard` rules ([checkDiscardTask]) ride along the same path so the discard check
 * runs in the consuming app's R8 — exactly where the matching `-assumevalues` rule applies.
 *
 * AGP 9.x does not propagate implicit Gradle task dependencies through
 * [LibraryVariant.consumerProguardFiles], so both tasks are also wired explicitly as a
 * dependency of every `export*ConsumerProguardFiles` task.
 */
internal fun wireProguardToLibraryVariants(
    project: Project,
    proguardTask: TaskProvider<GenerateProguardRulesTask>,
    checkDiscardTask: TaskProvider<GenerateCheckDiscardRulesTask>,
) {
    val libraryComponents =
        project.extensions
            .getByType(LibraryAndroidComponentsExtension::class.java)
    libraryComponents.onVariants { variant ->
        variant.consumerProguardFiles.add(
            proguardTask.flatMap { it.outputFile },
        )
        variant.consumerProguardFiles.add(
            checkDiscardTask.flatMap { it.outputFile },
        )
    }
    // AGP 9.x does not propagate implicit task dependencies through
    // variant.consumerProguardFiles, so we wire an explicit dependsOn on every
    // export*ConsumerProguardFiles task (AGP's task for packaging consumer rules into the AAR).
    project.tasks.configureEach { task ->
        if (task.name.startsWith("export") && task.name.endsWith("ConsumerProguardFiles")) {
            task.dependsOn(proguardTask)
            task.dependsOn(checkDiscardTask)
        }
    }
}

/**
 * Wires the generated ProGuard rules file as consumer ProGuard rules for every KMP Android
 * library variant.
 *
 * Called lazily — only when `com.android.kotlin.multiplatform.library` is present on the project.
 *
 * KMP Android library modules register [KotlinMultiplatformAndroidComponentsExtension] instead
 * of [LibraryAndroidComponentsExtension], so they require their own wiring function.
 *
 * Consumer ProGuard rules are bundled into the AAR and forwarded to every consuming app's R8,
 * which is where the `-assumevalues` rules need to run for flag dead-code elimination to work.
 * The `-checkdiscard` rules ([checkDiscardTask]) ride along the same path so the discard check
 * runs in the consuming app's R8.
 *
 * AGP 9.x does not propagate implicit Gradle task dependencies through
 * [KotlinMultiplatformAndroidVariant.consumerProguardFiles], so both tasks are also wired
 * explicitly as a dependency of every `export*ConsumerProguardFiles` task.
 */
internal fun wireProguardToKmpLibraryVariants(
    project: Project,
    proguardTask: TaskProvider<GenerateProguardRulesTask>,
    checkDiscardTask: TaskProvider<GenerateCheckDiscardRulesTask>,
) {
    val kmpComponents =
        project.extensions
            .getByType(KotlinMultiplatformAndroidComponentsExtension::class.java)
    kmpComponents.onVariants { variant ->
        variant.consumerProguardFiles.add(
            proguardTask.flatMap { it.outputFile },
        )
        variant.consumerProguardFiles.add(
            checkDiscardTask.flatMap { it.outputFile },
        )
    }
    project.tasks.configureEach { task ->
        if (task.name.startsWith("export") && task.name.endsWith("ConsumerProguardFiles")) {
            task.dependsOn(proguardTask)
            task.dependsOn(checkDiscardTask)
        }
    }
}
