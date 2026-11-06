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

// Fault injection for ReleaseDceGuaranteeTest. Mutates the generated rule file after it
// is written and before R8 reads it, so a green discard() cannot hide a no-op rule.
tasks.configureEach {
    if (name == "minifyReleaseWithR8") {
        doFirst {
            val mode = project.findProperty("featured.dce.mutateRules")?.toString() ?: return@doFirst
            val pro = file("build/featured/proguard-featured.pro")
            val original = pro.readText()
            val mutated =
                when (mode) {
                    "drop-assumevalues" -> {
                        original.replace(Regex("(?s)-assumevalues class [^{]+\\{[^{}]*}\\s*"), "")
                    }

                    "rename-method" -> {
                        original.replace("isDarkModeEnabled", "isDarkModeEnabledMissing")
                    }

                    else -> {
                        error("Unknown featured.dce.mutateRules=$mode")
                    }
                }
            check(mutated != original) { "Mutation $mode did not change ${pro.path}" }
            pro.writeText(mutated)
        }
    }
}
