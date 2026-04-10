plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
}

group = "dev.androidbroadcast.featured"

kotlin {
    explicitApi()
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("featured") {
            id = "dev.androidbroadcast.featured"
            implementationClass = "dev.androidbroadcast.featured.gradle.FeaturedPlugin"
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
    // Inject AGP into the TestKit subprocess via pluginUnderTestMetadata so that the Featured
    // plugin can access AndroidComponentsExtension when wireProguardToVariants() is called.
    testPluginClasspath("com.android.tools.build:gradle:9.1.0")
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.r8)
    testImplementation(libs.asm)
}
