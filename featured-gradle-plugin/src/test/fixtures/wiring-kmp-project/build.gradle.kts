plugins {
    // No explicit Kotlin version: withPluginClasspath() supplies the single Kotlin copy, so the
    // fixture compiles against the same Kotlin the plugin was built with. A separate `version`
    // would pull a second Kotlin from the fixture's repos and split the classloader in TestKit.
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.androidbroadcast.featured")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {}
    }
}

featured {
    localFlags {
        boolean("dark_mode", default = false)
    }
}
