import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.bcv)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidLibrary {
        namespace = "dev.androidbroadcast.featured.registry"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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
                withCompilations { it.target.name == "android" }
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
                header = "Code coverage summary for :featured-registry module"
                format = "Line coverage: <value>%"
            }

            verify {
                onCheck = true

                rule {
                    minBound(85)
                }
            }
        }
    }
}
