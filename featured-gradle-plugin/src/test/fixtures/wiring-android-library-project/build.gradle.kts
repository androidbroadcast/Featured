plugins {
    // AGP 9.1.0 brings the Kotlin plugin itself; applying a separate
    // org.jetbrains.kotlin.android plugin would register the `kotlin` extension twice
    // ("Cannot add extension 'kotlin', already registered"). Mirror android-project.
    id("com.android.library") version "9.1.0"
    id("dev.androidbroadcast.featured")
}

android {
    namespace = "dev.androidbroadcast.featured.wiringlib"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

featured {
    localFlags {
        boolean("dark_mode", default = false)
    }
}
