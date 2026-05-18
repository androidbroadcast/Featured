// The Featured plugin and Kotlin Multiplatform plugin are injected via GradleRunner.withPluginClasspath().
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kmp-publish-project"
include(":module")
