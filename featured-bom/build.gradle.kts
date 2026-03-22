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
        api(project(":datastore-provider"))
        api(project(":firebase-provider"))
        api(project(":sharedpreferences-provider"))
        api(project(":javaprefs-provider"))
        api(project(":featured-compose"))
        api(project(":featured-registry"))
        api(project(":featured-debug-ui"))
        api(project(":featured-gradle-plugin"))
        api(project(":nsuserdefaults-provider"))
        api(project(":featured-platform"))
        api(project(":featured-detekt-rules"))
        api(project(":configcat-provider"))
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
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
