plugins {
    id("dev.androidbroadcast.featured.internal.android-api-compat")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "dev.androidbroadcast.featured.appmetrica"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-appmetrica-provider",
    )
    pom {
        name.set("Featured AppMetrica Provider")
        description.set("AppMetrica remote flag provider for Featured – reads remote flags through Varioqub")
        inceptionYear.set("2024")
        url.set("https://github.com/AndroidBroadcast/Featured")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
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

dependencies {
    implementation(project(":core"))
    implementation(libs.varioqub.config)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExt.junit)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (!name.contains("Test")) {
        compilerOptions {
            freeCompilerArgs.add("-Xexplicit-api=strict")
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
                header = "Code coverage summary for :appmetrica-provider module"
                format = "Line coverage: <value>%"
            }

            verify {
                onCheck = true

                rule {
                    minBound(70)
                }
            }
        }
    }
}
