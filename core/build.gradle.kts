import co.touchlab.skie.configuration.DefaultArgumentInterop
import co.touchlab.skie.configuration.EnumInterop
import co.touchlab.skie.configuration.FlowInterop
import co.touchlab.skie.configuration.SealedInterop
import co.touchlab.skie.configuration.SuspendInterop
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.skie)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FeaturedCore"
            isStatic = true
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

android {
    namespace = "dev.androidbroadcast.featured.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
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
