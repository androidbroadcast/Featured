plugins {
    `java-platform`
    alias(libs.plugins.mavenPublish)
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":core"))
        api(project(":providers:datastore"))
        api(project(":providers:firebase"))
        api(project(":providers:sharedpreferences"))
        api(project(":providers:javaprefs"))
        api(project(":providers:nsuserdefaults"))
        api(project(":providers:configcat"))

        api(project(":featured-compose"))
        api(project(":featured-registry"))
        api(project(":featured-debug-ui"))
        api(project(":featured-gradle-plugin"))

        api(project(":featured-platform"))
        api(project(":featured-detekt-rules"))
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-bom",
    )
    pom {
        name.set("Featured BOM")
        description.set("Bill of Materials for Featured – type-safe, reactive KMP configuration management")
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
