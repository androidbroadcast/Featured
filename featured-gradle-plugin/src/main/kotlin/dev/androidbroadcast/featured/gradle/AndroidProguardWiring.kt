package dev.androidbroadcast.featured.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Wires the generated ProGuard rules file into every Android variant via the AGP Variant API.
 *
 * Called lazily — only when `com.android.application` or `com.android.library` is present on
 * the project. The task dependency is implicit through the [TaskProvider] chain, so no explicit
 * `dependsOn` is required.
 */
internal fun wireProguardToVariants(
    project: Project,
    proguardTask: TaskProvider<GenerateProguardRulesTask>,
) {
    val androidComponents =
        project.extensions
            .getByType(AndroidComponentsExtension::class.java)
    androidComponents.onVariants { variant ->
        variant.proguardFiles.add(
            proguardTask.flatMap { it.outputFile },
        )
    }
}
