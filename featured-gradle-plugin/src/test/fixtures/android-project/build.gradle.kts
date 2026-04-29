plugins {
    id("com.android.application") version "9.1.0"
    id("dev.androidbroadcast.featured")
}

android {
    namespace = "dev.androidbroadcast.featured.testapp"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Featured plugin auto-wires its proguard rules via the AGP Variant API.
            // A default keep file is required so R8 has something to keep.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

featured {
    localFlags {
        boolean("dark_mode", default = false)
    }
}
