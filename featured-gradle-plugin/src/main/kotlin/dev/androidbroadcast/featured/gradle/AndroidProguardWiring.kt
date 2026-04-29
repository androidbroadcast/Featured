package dev.androidbroadcast.featured.gradle

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Wires the generated ProGuard rules file into every Android variant via the AGP Variant API.
 *
 * Called lazily — only when `com.android.application` or `com.android.library` is present on
 * the project.
 *
 * AGP 9.x does not propagate implicit Gradle task dependencies through [Variant.proguardFiles],
 * so [proguardTask] is also wired explicitly as a dependency of every `minify*WithR8` task.
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
    // AGP 9.x does not propagate implicit task dependencies through variant.proguardFiles,
    // so we wire an explicit dependsOn on every R8 minify task.
    project.tasks.configureEach { task ->
        if (task.name.startsWith("minify") && task.name.endsWith("WithR8")) {
            task.dependsOn(proguardTask)
        }
    }
}
