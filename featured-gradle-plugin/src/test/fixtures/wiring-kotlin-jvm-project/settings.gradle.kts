// The Featured plugin and Kotlin/JVM plugin are injected via GradleRunner.withPluginClasspath().
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

rootProject.name = "wiring-kotlin-jvm-project"
