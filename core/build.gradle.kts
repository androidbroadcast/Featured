import co.touchlab.skie.configuration.DefaultArgumentInterop
import co.touchlab.skie.configuration.EnumInterop
import co.touchlab.skie.configuration.FlowInterop
import co.touchlab.skie.configuration.SealedInterop
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.skie)
    alias(libs.plugins.kover)
    alias(libs.plugins.bcv)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    android {
        namespace = "dev.androidbroadcast.featured.core"
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

    val xcf = XCFramework("FeaturedCore")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FeaturedCore"
            isStatic = true
            xcf.add(this)
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        @Suppress("unused")
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
    }
}

// Zips the release XCFramework for distribution via Swift Package Manager.
// Output: build/xcframeworks/FeaturedCore.xcframework.zip
val zipXCFramework by tasks.registering(Zip::class) {
    description = "Zips the release FeaturedCore XCFramework for SPM distribution"
    group = "build"

    dependsOn("assembleFeaturedCoreReleaseXCFramework")

    from(layout.buildDirectory.dir("XCFrameworks/release"))
    include("FeaturedCore.xcframework/**")
    archiveFileName.set("FeaturedCore.xcframework.zip")
    destinationDirectory.set(layout.buildDirectory.dir("xcframeworks"))
}

skie {
    features {
        coroutinesInterop.set(true)
        group {
            SuspendInterop.Enabled(true)
            FlowInterop.Enabled(true)
            DefaultArgumentInterop.Enabled(true)
            EnumInterop.Enabled(true)
            SealedInterop.Enabled(true) // or false
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-core",
    )
    pom {
        name.set("Featured Core")
        description.set("Core module for Featured – type-safe, reactive KMP configuration management")
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

kover {
    reports {
        filters {
            excludes {
                classes("*Test*", "*Mock*", "*Fake*")
            }
        }

        total {
            html {
                onCheck = false
            }

            xml {
                onCheck = false
            }

            log {
                onCheck = true
                header = "Code coverage summary for :core module"
                format = "Line coverage: <value>%"
            }

            verify {
                onCheck = true

                rule {
                    minBound(90)
                }
            }
        }
    }
}
