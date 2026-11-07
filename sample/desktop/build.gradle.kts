import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
import java.io.File
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(project(":sample:shared"))
        }
    }
}

evaluationDependsOn(":sample:feature-checkout")
val checkoutRules =
    project(":sample:feature-checkout").tasks.named("generateFeaturedProguardRules")
val checkoutRulesFile =
    project(":sample:feature-checkout").layout.buildDirectory.file("featured/proguard-featured.pro")

compose.desktop {
    application {
        mainClass = "dev.androidbroadcast.featured.MainDesktop"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.androidbroadcast.featured"
            // Matches versionName in :sample:android-app
            packageVersion = "1.0.0"
        }

        // ProGuard 7.8 honors -assumevalues and rejects R8 -checkdiscard, so only the
        // generated assumevalues file is added. The file provider plus dependsOn below
        // keep this configuration-cache safe: no Project at execution.
        buildTypes.release.proguard {
            configurationFiles.from(checkoutRulesFile)
            configurationFiles.builtBy(checkoutRules)
        }
    }
}

tasks.withType<AbstractProguardTask>().configureEach {
    if (name == "proguardReleaseJars") {
        dependsOn(checkoutRules)
    }
}

tasks.register(
    "proguardUnfoldedMarker",
    ProguardUnfoldedMarkerTask::class.java,
    Action<ProguardUnfoldedMarkerTask> {
        group = "verification"
        description =
            "ProGuard run of the release jars without the generated flag rules, so " +
            "NewCheckoutDisabledBranch stays when isNewCheckoutEnabled() is not folded."
        val release = tasks.named("proguardReleaseJars", AbstractProguardTask::class.java)
        inputFiles.from(release.map { it.inputFiles })
        mainJar.set(release.flatMap { it.mainJar })
        proguardClasspath.from(release.map { it.proguardFiles })
        defaultComposeRules.set(release.flatMap { it.defaultComposeRulesFile })
        javaHome.set(release.flatMap { it.javaHome })
        proguardVersion.set(release.flatMap { it.proguardVersion })
        releaseMainClass.set(release.flatMap { it.mainClass })
        outputJar.set(layout.buildDirectory.file("featured/proguard-unfolded-marker.jar"))
    },
)

/**
 * Control for the sample flag-stripping runbook. Same inputs and keep as
 * `proguardReleaseJars`, without `proguard-featured.pro`. One output jar.
 */
abstract class ProguardUnfoldedMarkerTask : DefaultTask() {
    @get:InputFiles
    @get:Classpath
    abstract val inputFiles: ConfigurableFileCollection

    @get:InputFile
    @get:Classpath
    abstract val mainJar: RegularFileProperty

    @get:InputFiles
    @get:Classpath
    abstract val proguardClasspath: ConfigurableFileCollection

    @get:InputFile
    abstract val defaultComposeRules: RegularFileProperty

    @get:Input
    abstract val javaHome: Property<String>

    @get:Input
    abstract val proguardVersion: Property<String>

    @get:Input
    abstract val releaseMainClass: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun runProguard() {
        check(proguardVersion.get() == "7.8.0") {
            "proguardUnfoldedMarker must use ProGuard 7.8.0, was ${proguardVersion.get()}."
        }
        check(proguardClasspath.files.any { it.name.contains("proguard-gradle-7.8.0") }) {
            "proguardUnfoldedMarker classpath is not ProGuard 7.8.0: " +
                proguardClasspath.files.joinToString { it.name }
        }
        check(releaseMainClass.get() == "dev.androidbroadcast.featured.MainDesktop") {
            "proguardReleaseJars main class is ${releaseMainClass.get()}, expected MainDesktop."
        }
        val rules = defaultComposeRules.get().asFile
        check(rules.isFile) { "Missing Compose default rules at ${rules.absolutePath}." }
        check(!rules.name.contains("proguard-featured")) {
            "proguardUnfoldedMarker must not take generated featured rules."
        }

        val jars = linkedSetOf<File>()
        jars += mainJar.get().asFile
        inputFiles.files.forEach { file ->
            if (file.isDirectory || file.name.endsWith(".jar", ignoreCase = true)) {
                jars += file
            }
        }

        val home = File(javaHome.get())
        val jmods =
            home
                .resolve("jmods")
                .walk()
                .filter { jmod ->
                    jmod.isFile && jmod.path.endsWith("jmod", ignoreCase = true)
                }.toList()
        check(jmods.isNotEmpty()) { "No JDK jmods under ${home.absolutePath}." }

        val out = outputJar.get().asFile
        out.parentFile.mkdirs()
        if (out.exists()) out.delete()

        val config = temporaryDir.resolve("unfolded.pro")
        config.writeText(proguardConfig(jars, jmods, rules, out))
        val text = config.readText()
        check(!text.contains("proguard-featured") && !text.contains("-assumevalues")) {
            "proguardUnfoldedMarker config must not include featured rules."
        }
        check(!text.contains("-keep") || !text.contains("NewCheckoutDisabledBranch")) {
            "proguardUnfoldedMarker must not keep NewCheckoutDisabledBranch."
        }

        execOperations.javaexec {
            executable = home.resolve("bin/java").absolutePath
            classpath(proguardClasspath)
            mainClass.set("proguard.ProGuard")
            maxHeapSize = "2g"
            args("-include", config.absolutePath)
        }

        logger.lifecycle("proguardUnfoldedMarker wrote ${out.absolutePath}")
    }

    private fun proguardConfig(
        jars: Set<File>,
        jmods: List<File>,
        rules: File,
        out: File,
    ): String =
        buildString {
            appendLine("-dontobfuscate")
            appendLine("-keep public class dev.androidbroadcast.featured.MainDesktop {")
            appendLine("    public static void main(java.lang.String[]);")
            appendLine("}")
            appendLine("-include '${rules.absolutePath}'")
            jars.forEachIndexed { index, jar ->
                val filter = if (index == 0) "" else "(!META-INF/**)"
                appendLine("-injars '${jar.absolutePath}'$filter")
            }
            appendLine("-outjars '${out.absolutePath}'")
            jmods.forEach { jmod ->
                appendLine("-libraryjars '${jmod.absolutePath}'(!**.jar;!module-info.class)")
            }
        }
}
