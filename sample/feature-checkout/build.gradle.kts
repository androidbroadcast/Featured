import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    id("dev.androidbroadcast.featured")
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "dev.androidbroadcast.featured.sample.checkout"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // AGP 9 KMP libraries do not export consumer ProGuard rules unless this is set.
        // The featured plugin already adds the generated files; this publishes them to the app R8.
        optimization {
            consumerKeepRules.publish = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.kotlinx.coroutines.core)
            // CheckoutFlagsViewModel is in this module's public API surface.
            api(libs.androidx.lifecycle.viewmodel)
        }
    }
}

featured {
    localFlags {
        boolean("new_checkout", default = false) {
            description = "Enable the redesigned checkout flow"
            category = "checkout"
            discard("dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch")
        }
        enum(
            key = "checkout_variant",
            typeFqn = "dev.androidbroadcast.featured.sample.checkout.CheckoutVariant",
            default = "LEGACY",
        ) {
            description = "Controls which checkout flow variant is shown to the user"
            category = "checkout"
        }
    }
}
