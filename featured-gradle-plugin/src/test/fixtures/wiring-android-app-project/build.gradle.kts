plugins {
    // AGP 9.1.0 brings the Kotlin plugin itself; applying a separate
    // org.jetbrains.kotlin.android plugin would register the `kotlin` extension twice
    // ("Cannot add extension 'kotlin', already registered"). Mirror android-project.
    // AGP keeps its explicit version: AGP is NOT on the plugin classpath (compileOnly), so
    // withPluginClasspath() cannot supply it — only the featured plugin comes from there.
    id("com.android.application") version "9.1.0"
    id("dev.androidbroadcast.featured")
}

android {
    namespace = "dev.androidbroadcast.featured.wiringapp"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }
}

featured {
    localFlags {
        boolean("dark_mode", default = false)
    }
}
