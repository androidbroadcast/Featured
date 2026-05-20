@file:Suppress("UnstableApiUsage")

// pluginManagement must be the first block per Gradle's settings-script rules.

pluginManagement {
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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// Propagate VERSION_NAME (and any other properties) from the root gradle.properties into
// this included build so that Vanniktech maven-publish resolves the correct version.
// The root gradle.properties is the single source of truth — do not duplicate VERSION_NAME here.
//
// providers.fileContents() registers the file with Gradle's configuration-cache fingerprint,
// unlike a raw FileInputStream read which is invisible to the cache. When root gradle.properties
// changes (e.g. VERSION_NAME bump), the cache entry is invalidated and the new value is picked up.
val parentPropertiesText: Provider<String> =
    providers.fileContents(layout.rootDirectory.file("../gradle.properties")).asText

gradle.beforeProject {
    val parentProps =
        java.util.Properties().apply {
            parentPropertiesText.orNull?.reader()?.use { load(it) }
        }
    parentProps.forEach { key, value ->
        extensions.extraProperties[key.toString()] = value.toString()
    }
    // Set project.version directly so Vanniktech's providers.gradleProperty fallback also works.
    (parentProps.getProperty("VERSION_NAME") ?: "unspecified").let { version = it }
}

rootProject.name = "build-logic"
include(":featured-gradle-plugin")
