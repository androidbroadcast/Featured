import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

fun appMetricaBuildConfigValue(name: String): String {
    val literal =
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .orElse("")
            .map { text ->
                val properties = Properties()
                if (text.isNotEmpty()) {
                    properties.load(text.reader())
                }
                properties.getProperty(name).orEmpty().toJavaStringLiteral()
            }.get()
    return literal
}

fun String.toJavaStringLiteral(): String =
    buildString {
        append('"')
        for (char in this@toJavaStringLiteral) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
        append('"')
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
        buildConfigField("String", "APPMETRICA_API_KEY", appMetricaBuildConfigValue("appmetrica.apiKey"))
        buildConfigField("String", "APPMETRICA_CLIENT_ID", appMetricaBuildConfigValue("appmetrica.clientId"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    implementation(project(":providers:appmetrica"))
    implementation(libs.varioqub.config)
    implementation(libs.varioqub.appmetrica.adapter)
    implementation(libs.appmetrica.analytics)
    implementation(project(":sample:shared"))
    implementation(project(":featured-debug-ui"))
    implementation(project(":featured-platform"))
    implementation(project(":providers:datastore"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    // viewModel { } composable used in setContent to scope VMs to the Activity ViewModelStore.
    implementation(libs.androidx.lifecycle.viewmodelCompose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.testExt.junit)
}
