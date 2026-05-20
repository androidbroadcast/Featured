plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's class.javaloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.dokka)
}

tasks.register("publishToMavenCentral") {
    dependsOn(gradle.includedBuild("build-logic").task(":featured-gradle-plugin:publishToMavenCentral"))
}

tasks.register("publishToMavenLocal") {
    dependsOn(gradle.includedBuild("build-logic").task(":featured-gradle-plugin:publishToMavenLocal"))
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts")
        ktlint(ktlintVersion)
    }
}
