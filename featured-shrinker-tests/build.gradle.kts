plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.r8)
    testImplementation(libs.asm)
}
