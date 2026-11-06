import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlinJvm)
    id("dev.androidbroadcast.featured")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The plugin auto-wires generated ConfigParam sources into this Kotlin/JVM compilation.
    // Those sources import the Featured runtime, so the module cannot compile without :core.
    implementation(project(":core"))
    testImplementation(libs.kotlin.testJunit)
}

featured {
    localFlags {
        boolean("new_checkout", default = false)
    }
}

val swiftDceTestArguments = objects.newInstance(SwiftDceTestArguments::class.java)
swiftDceTestArguments.xcconfig.set(layout.buildDirectory.file("featured/FeatureFlags.generated.xcconfig"))
swiftDceTestArguments.fixture.set(layout.projectDirectory.file("src/test/swift/GuardedCheckout.swift"))
swiftDceTestArguments.requireSwiftDce.set(providers.gradleProperty("requireSwiftDce").orElse("false"))

tasks.named<Test>("test") {
    dependsOn(tasks.named("generateXcconfig"))
    inputs.file(swiftDceTestArguments.xcconfig)
    inputs.file(swiftDceTestArguments.fixture)
    inputs.property("requireSwiftDce", swiftDceTestArguments.requireSwiftDce)
    jvmArgumentProviders.add(swiftDceTestArguments)
}

/**
 * Execution-time `-D` values for [SwiftFlagStrippingTest]. The xcconfig path is the
 * `generateXcconfig` output, resolved when the test worker starts, not a hand-written condition.
 */
abstract class SwiftDceTestArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val xcconfig: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixture: RegularFileProperty

    @get:Input
    abstract val requireSwiftDce: Property<String>

    override fun asArguments(): Iterable<String> =
        listOf(
            "-Dfeatured.swiftDce.xcconfig=${xcconfig.get().asFile.absolutePath}",
            "-Dfeatured.swiftDce.fixture=${fixture.get().asFile.absolutePath}",
            "-Dfeatured.swiftDce.require=${requireSwiftDce.get()}",
        )
}
