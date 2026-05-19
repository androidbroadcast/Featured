@file:Suppress("UnstableApiUsage")

// Propagate VERSION_NAME (and any other properties) from the root gradle.properties into
// this included build so that Vanniktech maven-publish resolves the correct version.
// The root gradle.properties is the single source of truth — do not duplicate VERSION_NAME here.
val parentProps = java.util.Properties().apply {
    rootDir.parentFile.resolve("gradle.properties").inputStream().use { load(it) }
}
gradle.beforeProject {
    parentProps.forEach { key, value ->
        extensions.extraProperties[key.toString()] = value.toString()
    }
    // Set project.version directly so Vanniktech's providers.gradleProperty fallback also works.
    (parentProps.getProperty("VERSION_NAME") ?: "unspecified").let { version = it }
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

rootProject.name = "build-logic"
include(":featured-gradle-plugin")
