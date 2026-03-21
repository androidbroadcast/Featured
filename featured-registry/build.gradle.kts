import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            baseName = "FeaturedRegistry"
            isStatic = true
        }
    }

    jvm()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withAndroidTarget()
            }
            group("native") {
                withIosX64()
                withIosArm64()
                withIosSimulatorArm64()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core)
        }

        @Suppress("unused")
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-registry",
    )
    pom {
        name.set("Featured Registry")
        description.set("Registry module for Featured – runtime flag registration and lookup for KMP")
        inceptionYear.set("2024")
        url.set("https://github.com/AndroidBroadcast/Featured")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("androidbroadcast")
                name.set("Kirill Rozov")
                url.set("https://github.com/androidbroadcast")
            }
        }
        scm {
            url.set("https://github.com/AndroidBroadcast/Featured")
            connection.set("scm:git:git://github.com/AndroidBroadcast/Featured.git")
            developerConnection.set("scm:git:ssh://git@github.com/AndroidBroadcast/Featured.git")
        }
    }
}

android {
    namespace = "dev.androidbroadcast.featured.registry"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
