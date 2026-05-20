plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.androidbroadcast.featured.sample.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.androidbroadcast.featured.sample"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
}

dependencies {
    implementation(project(":sample:shared"))
    implementation(project(":featured-debug-ui"))
    implementation(project(":featured-platform"))
    implementation(project(":providers:datastore"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    // viewModel { } composable used in setContent to scope VMs to the Activity ViewModelStore.
    implementation(libs.androidx.lifecycle.viewmodelCompose)
}
