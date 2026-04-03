plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.bcv)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

android {
    namespace = "dev.androidbroadcast.featured.sharedpreferences"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-sharedpreferences-provider",
    )
    pom {
        name.set("Featured SharedPreferences Provider")
        description.set("SharedPreferences provider for Featured – persists configuration flags via Android SharedPreferences")
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

dependencies {
    implementation(project(":core"))

    // Android preferences
    implementation(libs.androidx.preference)
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.testExt.junit)
    testImplementation(libs.turbine)
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
                header = "Code coverage summary for :sharedpreferences-provider module"
                format = "Line coverage: <value>%"
            }

            verify {
                onCheck = true

                rule {
                    minBound(80)
                }
            }
        }
    }
}
