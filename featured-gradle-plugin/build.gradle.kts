plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

group = "dev.androidbroadcast.featured"

kotlin {
    explicitApi()
    jvmToolchain(21)
}

gradlePlugin {
    plugins {
        create("featured") {
            id = "dev.androidbroadcast.featured"
            implementationClass = "dev.androidbroadcast.featured.gradle.FeaturedPlugin"
        }
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.testJunit)
}
