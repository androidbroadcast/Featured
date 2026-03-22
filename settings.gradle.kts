rootProject.name = "Featured"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {

    @Suppress("UnstableApiUsage")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {

    @Suppress("UnstableApiUsage")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":featured-gradle-plugin")
include(":sample")
include(":core")
include(":datastore-provider")
include(":firebase-provider")
include(":sharedpreferences-provider")
include(":featured-compose")
include(":featured-registry")
include(":featured-debug-ui")
include(":featured-testing")
include(":javaprefs-provider")
include(":nsuserdefaults-provider")
include(":featured-platform")
include(":featured-bom")
include(":featured-detekt-rules")
