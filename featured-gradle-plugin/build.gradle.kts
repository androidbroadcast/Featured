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

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.r8)
    testImplementation(libs.asm)
}
