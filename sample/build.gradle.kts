import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Set to true (or pass -PhasFirebase=true) when google-services.json is present.
val hasFirebase =
    project.findProperty("hasFirebase") == "true" ||
        rootProject.file("sample/google-services.json").exists()

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.skie)
}

kotlin {
    jvmToolchain(21)

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FeaturedSampleApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(project(":featured-compose"))
            implementation(project(":featured-platform"))
            // SharedPreferences-backed local provider (alternative to DataStore)
            implementation(project(":sharedpreferences-provider"))
            if (hasFirebase) {
                // Firebase Remote Config provider — requires google-services.json
                implementation(project(":firebase-provider"))
            }
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(project(":core"))
            implementation(project(":featured-registry"))
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "dev.androidbroadcast.featured"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.androidbroadcast.featured"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "HAS_FIREBASE", "$hasFirebase")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    debugImplementation(project(":featured-debug-ui"))
    if (hasFirebase) {
        // Firebase BOM must live in the legacy dependencies block in KMP projects
        // because platform() is not available inside kotlin { sourceSets { } }.
        add("androidMainImplementation", platform(libs.firebase.bom))
        add("androidMainImplementation", libs.firebase.config)
    }
}

compose.desktop {
    application {
        mainClass = "dev.androidbroadcast.featured.MainDesktop"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.androidbroadcast.featured"
            packageVersion = "1.0.0"
        }
    }
}
