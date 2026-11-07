import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-gradle-plugin`
}

group = "dev.androidbroadcast.featured.internal"

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Unpublished convention. No maven-publish and no plugin-publish, so Gradle does not
// create a plugin marker artifact. The plugin id is only visible through includeBuild.
gradlePlugin {
    plugins {
        create("androidApiCompat") {
            id = "dev.androidbroadcast.featured.internal.android-api-compat"
            implementationClass = "dev.androidbroadcast.featured.apicompat.AndroidApiCompatPlugin"
            displayName = "Featured Android API compatibility (internal)"
            description = "Unpublished dump-and-check for the public Android surface."
        }
    }
}

val abiTools: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-build-tools-api:${libs.versions.kotlin.get()}")
    abiTools("org.jetbrains.kotlin:kotlin-build-tools-impl:${libs.versions.kotlin.get()}")
    testImplementation(libs.kotlin.testJunit)
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}

tasks.test {
    val tools = configurations.named("abiTools")
    jvmArgumentProviders.add(toolsClasspathArgument(tools))
    systemProperty("featured.repoRoot", rootDir.parentFile.parentFile.absolutePath)
}

fun toolsClasspathArgument(tools: Provider<Configuration>): CommandLineArgumentProvider {
    val provider = objects.newInstance(AbiToolsArgumentProvider::class.java)
    provider.files.from(tools)
    return provider
}

abstract class AbiToolsArgumentProvider : CommandLineArgumentProvider {
    @get:Classpath
    abstract val files: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf("-Dandroid.api.compat.tools=${files.asPath}")
}
