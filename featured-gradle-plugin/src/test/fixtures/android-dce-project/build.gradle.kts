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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

featured {
    localFlags {
        boolean("dark_mode", default = false) {
            discard("dev.androidbroadcast.featured.testapp.DeadFeature")
        }
    }
}
