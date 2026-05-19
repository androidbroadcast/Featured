import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    id("dev.androidbroadcast.featured")
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "dev.androidbroadcast.featured.sample.ui"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    )

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.coroutines.core)
        }
    }

    sourceSets.commonMain.get().kotlin.srcDir(
        tasks.named("generateConfigParam").map { it.outputs.files.singleFile },
    )
}

featured {
    localFlags {
        boolean("main_button_red", default = true) {
            description = "Tint the main button red (otherwise blue)"
            category = "ui"
        }
        boolean("new_feature_section_enabled", default = true) {
            description = "Show the new-feature section on the main screen"
            category = "ui"
        }
    }
}
