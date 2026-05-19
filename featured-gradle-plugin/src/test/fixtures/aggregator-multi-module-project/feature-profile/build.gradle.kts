plugins {
    id("com.android.library") version "9.1.0"
    id("dev.androidbroadcast.featured")
}

android {
    namespace = "com.example.featureprofile"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

featured {
    localFlags {
        string("avatar_placeholder", default = "default.png")
    }
    remoteFlags {
        boolean("show_avatar", default = true)
    }
}
