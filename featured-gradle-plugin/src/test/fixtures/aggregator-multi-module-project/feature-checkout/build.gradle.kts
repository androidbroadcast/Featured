plugins {
    id("com.android.library") version "9.1.0"
    id("dev.androidbroadcast.featured")
}

android {
    namespace = "com.example.featurecheckout"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

featured {
    localFlags {
        boolean("dark_mode", default = false) { category = "UI" }
        enum("checkout_variant", typeFqn = "com.example.CheckoutVariant", default = "LEGACY")
    }
}
