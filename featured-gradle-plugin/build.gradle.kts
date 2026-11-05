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

// Plugin markers must be published to Maven Central, not only to the Plugin Portal.
//
// `plugins { id("dev.androidbroadcast.featured") version "..." }` resolves the marker
// `dev.androidbroadcast.featured:dev.androidbroadcast.featured.gradle.plugin` from
// gradlePluginPortal(). The Portal does not host an unapproved plugin: it answers with
// 303 See Other to Maven Central. Portal publication of 1.0.0+ succeeded and created
// version records, but the plugin was never approved, so those artifacts are not served.
// Stable markers were removed from Central in #228, which left every release after
// 1.0.0-Beta1 uninstallable via the documented plugins DSL.
//
// The extra Central components are the resolution mechanism. `publishPlugins` stays for
// Portal listing once Gradle approves the plugin id; it is not the install path.
// Do not disable these tasks again.
val pluginMarkerMavenCentralTasks =
    listOf(
        "publishFeaturedPluginMarkerMavenPublicationToMavenCentralRepository",
        "publishFeaturedApplicationPluginMarkerMavenPublicationToMavenCentralRepository",
    )

afterEvaluate {
    val missing = pluginMarkerMavenCentralTasks.filter { tasks.findByName(it) == null }
    val disabled =
        pluginMarkerMavenCentralTasks.mapNotNull { tasks.findByName(it) }.filter { !it.enabled }
    if (missing.isNotEmpty() || disabled.isNotEmpty()) {
        throw GradleException(
            "Plugin marker Maven Central publications must stay enabled. " +
                "missing=$missing disabled=${disabled.map { it.name }}. " +
                "plugins {} resolves these markers via the Plugin Portal redirect to Maven Central.",
        )
    }
}

tasks.named("publishToMavenCentral") {
    dependsOn(pluginMarkerMavenCentralTasks)
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
