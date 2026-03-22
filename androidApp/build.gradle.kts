// Set to true (or pass -PhasFirebase=true) when google-services.json is present.
val hasFirebase =
    project.findProperty("hasFirebase") == "true" ||
        rootProject.file("sample/google-services.json").exists()

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

android {
    namespace = "dev.androidbroadcast.featured"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.androidbroadcast.featured"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
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
    implementation(project(":sample"))
    implementation(project(":featured-registry"))

    implementation(compose.preview)
    implementation(libs.androidx.activity.compose)
    implementation(project(":featured-compose"))
    implementation(project(":featured-platform"))
    implementation(project(":sharedpreferences-provider"))

    debugImplementation(compose.uiTooling)
    debugImplementation(project(":featured-debug-ui"))

    if (hasFirebase) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.config)
        implementation(project(":firebase-provider"))
    }
}
