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
include(":androidApp")
include(":sample")
include(":core")
include(":datastore-provider")
project(":datastore-provider").projectDir = file("providers/datastore-provider")
include(":firebase-provider")
project(":firebase-provider").projectDir = file("providers/firebase-provider")
include(":sharedpreferences-provider")
project(":sharedpreferences-provider").projectDir = file("providers/sharedpreferences-provider")
include(":featured-compose")
include(":featured-registry")
include(":featured-debug-ui")
include(":featured-testing")
include(":javaprefs-provider")
project(":javaprefs-provider").projectDir = file("providers/javaprefs-provider")
include(":nsuserdefaults-provider")
project(":nsuserdefaults-provider").projectDir = file("providers/nsuserdefaults-provider")
include(":featured-platform")
include(":featured-bom")
include(":featured-detekt-rules")
include(":configcat-provider")
project(":configcat-provider").projectDir = file("providers/configcat-provider")
include(":featured-lint-rules")
