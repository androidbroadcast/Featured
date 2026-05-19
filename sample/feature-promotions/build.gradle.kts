import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    id("dev.androidbroadcast.featured")
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "dev.androidbroadcast.featured.sample.promotions"
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

    iosX64()
    iosArm64()
    iosSimulatorArm64()

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
    remoteFlags {
        boolean("promo_banner_enabled", default = false) {
            description = "Show the promotional banner on the main screen"
            category = "promotions"
        }
    }
}
