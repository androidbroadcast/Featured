import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.skie)
    id("dev.androidbroadcast.featured.application")
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "dev.androidbroadcast.featured.sample"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FeaturedSampleApp"
            isStatic = true
            export(project(":sample:feature-checkout"))
            export(project(":sample:feature-promotions"))
            export(project(":sample:feature-ui"))
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // :core types (ConfigValues, InMemoryConfigValueProvider) appear in
            // the public signatures of SampleApp — must be api to compile
            // downstream consumers like :sample:desktop. Pre-existing leak from #182.
            api(project(":core"))

            // Per-feature ViewModel types (CheckoutFlagsViewModel, PromotionsFlagsViewModel,
            // UiFlagsViewModel) appear in SampleApp's public signature — api so that platform
            // shells (:sample:android-app, :sample:desktop, iosMain) can reference them.
            api(project(":sample:feature-checkout"))
            api(project(":sample:feature-promotions"))
            api(project(":sample:feature-ui"))
        }
    }

    sourceSets.commonMain.get().kotlin.srcDir(
        tasks.named("generateFeaturedRegistry").map { it.outputs.files.singleFile.parentFile },
    )
}

dependencies {
    featuredAggregation(project(":sample:feature-checkout"))
    featuredAggregation(project(":sample:feature-promotions"))
    featuredAggregation(project(":sample:feature-ui"))
}
