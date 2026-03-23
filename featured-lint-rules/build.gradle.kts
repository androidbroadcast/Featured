plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.bcv)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.lint.api)
    // lint-tests does not transitively expose lint-api on the test classpath
    testImplementation(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.kotlin.testJunit)
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "dev.androidbroadcast.featured.lint.FeaturedIssueRegistry")
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-lint-rules",
    )
    pom {
        name.set("Featured Lint Rules")
        description.set("Custom Android Lint rules for Featured – enforce correct feature flag usage")
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
