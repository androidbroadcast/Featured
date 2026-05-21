plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.10"
    id("dev.androidbroadcast.featured")
    id("maven-publish")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {}
    }
}

group = "com.example.test"
version = "0.1.0"

featured {
    localFlags {
        boolean("debug_overlay", default = false)
    }
}

publishing {
    repositories {
        maven {
            name = "TestLocal"
            url = uri(layout.buildDirectory.dir("test-repo"))
        }
    }
}
