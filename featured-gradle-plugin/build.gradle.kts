import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.mavenPublish)
}

group = "dev.androidbroadcast.featured"

kotlin {
    explicitApi()
    jvmToolchain(17)
}

gradlePlugin {
    website.set("https://github.com/AndroidBroadcast/Featured")
    vcsUrl.set("https://github.com/AndroidBroadcast/Featured")
    plugins {
        create("featured") {
            id = "dev.androidbroadcast.featured"
            implementationClass = "dev.androidbroadcast.featured.gradle.FeaturedPlugin"
            displayName = "Featured Gradle Plugin"
            description = "Gradle plugin for Featured – generates type-safe configuration flag declarations"
            tags.set(listOf("featured", "feature-flags", "configuration", "codegen", "kotlin-multiplatform"))
        }
        create("featuredApplication") {
            id = "dev.androidbroadcast.featured.application"
            implementationClass = "dev.androidbroadcast.featured.gradle.FeaturedApplicationPlugin"
            displayName = "Featured Application Gradle Plugin"
            description = "Featured aggregator plugin – merges per-module flag manifests into a single generated registry"
            tags.set(listOf("featured", "feature-flags", "configuration", "codegen", "kotlin-multiplatform"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-gradle-plugin",
    )
    pom {
        name.set("Featured Gradle Plugin")
        description.set("Gradle plugin for Featured – generates type-safe configuration flag declarations")
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

// The java-gradle-plugin marker artifacts (one per plugin id, the second under its own
// groupId) are resolved via the Gradle Plugin Portal, where `publishPlugins` uploads them.
// Vanniktech binds every MavenPublication — markers included — to the Maven Central
// repository, which would pollute the Central listing with two stub marker artifacts.
// Disable only the marker -> Central tasks so Central carries just the clean
// `featured-gradle-plugin` impl jar (+ sources/javadoc/pom); the Portal still receives the
// markers via `publishPlugins`, which uploads publications directly over HTTP independently
// of these maven-publish repository tasks.
//
// Match on the task name (which Gradle fixes at registration time) rather than the task's
// `repository`/`publication` properties: maven-publish wires those properties later, in an
// afterEvaluate, so a `configureEach` action that reads them runs too early and sees them
// unset. The name `publish<Pub>PublicationTo<Repo>Repository` is always correct here.
// `publishPluginMavenPublication...` (the impl) ends with `PluginMavenPublication...`, not
// `PluginMarkerMavenPublication...`, so it is intentionally left enabled.
tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("PluginMarkerMavenPublicationToMavenCentralRepository")) {
        enabled = false
    }
}

// A separate configuration whose resolved jars are appended to the pluginUnderTestMetadata
// classpath. This makes GradleRunner.withPluginClasspath() inject them into the TestKit
// subprocess, which is necessary for compileOnly dependencies (like AGP) that the plugin
// needs at runtime but that java-gradle-plugin does not include from runtimeClasspath.
val testPluginClasspath: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(testPluginClasspath)
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.1.0")
    implementation(libs.kotlinx.serialization.json)
    // Inject AGP into the TestKit subprocess via pluginUnderTestMetadata so that the Featured
    // plugin can access AndroidComponentsExtension when wireProguardToVariants() is called.
    testPluginClasspath("com.android.tools.build:gradle:9.1.0")
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.testJunit)
}
