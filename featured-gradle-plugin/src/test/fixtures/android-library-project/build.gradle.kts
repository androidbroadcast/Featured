plugins {
    id("com.android.library") version "9.1.0"
    id("dev.androidbroadcast.featured")
}

android {
    namespace = "dev.androidbroadcast.featured.testlib"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

featured {
    localFlags {
        boolean("dark_mode", default = false)
    }
}
