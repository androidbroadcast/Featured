plugins {
    id("com.android.application") version "9.1.0"
    id("dev.androidbroadcast.featured")
    id("dev.androidbroadcast.featured.application")
}

android {
    namespace = "com.example.testapp"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    featuredAggregation(project(":feature-checkout"))
    featuredAggregation(project(":feature-profile"))
}
