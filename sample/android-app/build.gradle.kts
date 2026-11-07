import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

fun appMetricaBuildConfigValue(name: String): String {
    val literal =
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .orElse("")
            .map { text ->
                val properties = Properties()
                if (text.isNotEmpty()) {
                    properties.load(text.reader())
                }
                properties.getProperty(name).orEmpty().toJavaStringLiteral()
            }.get()
    return literal
}

fun String.toJavaStringLiteral(): String =
    buildString {
        append('"')
        for (char in this@toJavaStringLiteral) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
        append('"')
    }

android {
    namespace = "dev.androidbroadcast.featured.sample.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "dev.androidbroadcast.featured.sample"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "APPMETRICA_API_KEY", appMetricaBuildConfigValue("appmetrica.apiKey"))
        buildConfigField("String", "APPMETRICA_CLIENT_ID", appMetricaBuildConfigValue("appmetrica.clientId"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
}

// R8 for the unfolded control only. Not an app runtime dependency.
val r8Tool =
    configurations.create("r8Tool") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    implementation(project(":providers:appmetrica"))
    implementation(libs.varioqub.config)
    implementation(libs.varioqub.appmetrica.adapter)
    implementation(libs.appmetrica.analytics)
    implementation(project(":sample:shared"))
    implementation(project(":featured-debug-ui"))
    implementation(project(":featured-platform"))
    implementation(project(":providers:datastore"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    // viewModel { } composable used in setContent to scope VMs to the Activity ViewModelStore.
    implementation(libs.androidx.lifecycle.viewmodelCompose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.testExt.junit)
    add(r8Tool.name, libs.r8)
}

val androidComponents = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
val defaultOptimizeRules =
    extensions.getByType(ApplicationExtension::class.java).getDefaultProguardFile("proguard-android-optimize.txt")

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val r8UnfoldedMarker =
        tasks.register(
            "r8UnfoldedMarker",
            R8UnfoldedMarkerTask::class.java,
            Action<R8UnfoldedMarkerTask> {
                group = "verification"
                description =
                    "R8 class-file run of release classes without the generated flag rules, so " +
                    "NewCheckoutDisabledBranch stays when isNewCheckoutEnabled() is not folded."
                outputJar.set(layout.buildDirectory.file("featured/r8-unfolded-marker.jar"))
                collectiveConfig.set(layout.buildDirectory.file("featured/r8-unfolded-marker.pro"))
                optimizeRules.set(defaultOptimizeRules)
                dependsOn("extractProguardFiles")
                r8Classpath.from(r8Tool)
                bootClasspath.from(androidComponents.sdkComponents.bootClasspath)
            },
        )
    // Pre-R8 classes. PROJECT is the app compilation. ALL adds dependency classes, already
    // extracted by AGP, so this is not the unsigned APK and not minifyReleaseWithR8 output.
    variant.artifacts
        .forScope(ScopedArtifacts.Scope.PROJECT)
        .use(r8UnfoldedMarker)
        .toGet(
            ScopedArtifact.CLASSES,
            R8UnfoldedMarkerTask::appJars,
            R8UnfoldedMarkerTask::appDirs,
        )
    variant.artifacts
        .forScope(ScopedArtifacts.Scope.ALL)
        .use(r8UnfoldedMarker)
        .toGet(
            ScopedArtifact.CLASSES,
            R8UnfoldedMarkerTask::dependencyJars,
            R8UnfoldedMarkerTask::dependencyDirs,
        )
}

/**
 * Control for the sample flag-stripping runbook. Not a replacement for assembleRelease.
 *
 * Program input is the release compilation plus the runtime jars that contain the checkout
 * bridge. android.jar is `--lib`. The generated featured rules are not inputs, so
 * `isNewCheckoutEnabled()` is not folded and the disabled class must survive.
 */
abstract class R8UnfoldedMarkerTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appDirs: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyDirs: ListProperty<Directory>

    @get:InputFiles
    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val optimizeRules: RegularFileProperty

    @get:Classpath
    abstract val r8Classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @get:OutputFile
    abstract val collectiveConfig: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun runR8() {
        val work = temporaryDir
        val extracted = work.resolve("extracted")
        extracted.mkdirs()

        val program = mutableListOf<File>()
        appDirs.get().map { it.asFile }.forEach { dir ->
            asClassContainer(dir, extracted)?.let { program += it }
        }
        appJars.get().map { it.asFile }.filter { it.isFile }.forEach { jar ->
            asClassContainer(jar, extracted)?.let { program += it }
        }
        check(program.none { it.extension.equals("apk", ignoreCase = true) }) {
            "r8UnfoldedMarker program input must be pre-R8 classes, not an APK."
        }

        val classpath = mutableListOf<File>()
        val markerCandidates = mutableListOf<File>()
        val alreadyProgram = program.map { it.absolutePath }.toMutableSet()
        val dependencyInputs =
            dependencyDirs.get().map { it.asFile } + dependencyJars.get().map { it.asFile }
        dependencyInputs.forEach { file ->
            val readable = asClassContainer(file, extracted) ?: return@forEach
            if (!alreadyProgram.add(readable.absolutePath)) return@forEach
            if (containsClass(readable, MARKER_ENTRY) || containsClass(readable, BRIDGE_ENTRY)) {
                markerCandidates += readable
            } else {
                classpath += readable
            }
        }
        val markerJar =
            markerCandidates.firstOrNull { containsClass(it, MARKER_ENTRY) && containsClass(it, BRIDGE_ENTRY) }
                ?: markerCandidates.firstOrNull { containsClass(it, MARKER_ENTRY) }
        checkNotNull(markerJar) {
            "Release runtime classpath has no NewCheckoutDisabledBranch. " +
                "r8UnfoldedMarker cannot show the class present when the accessor is not folded."
        }
        program += markerJar

        val out = outputJar.get().asFile
        out.parentFile.mkdirs()
        if (out.exists()) out.delete()
        ZipOutputStream(out.outputStream()).close()

        val keep = work.resolve("keep-on-create.pro")
        // Class-file R8 replaces this onCreate with throw null when the optimize file's
        // optimizer runs on the partial program, so the entry-point keep would not retain
        // the call. -dontoptimize is this task only. assumevalues still folds the accessor
        // without it; the release minify task does not use this file.
        keep.writeText(
            """
            -dontoptimize
            -keep class dev.androidbroadcast.featured.sample.MainActivity {
                public void onCreate(android.os.Bundle);
                protected void onCreate(android.os.Bundle);
            }
            """.trimIndent() + "\n",
        )
        val optimize = optimizeRules.get().asFile
        check(!optimize.name.contains("proguard-featured")) {
            "r8UnfoldedMarker must not take generated featured rules."
        }

        val collective = collectiveConfig.get().asFile
        collective.parentFile.mkdirs()
        val args =
            buildList {
                add("--classfile")
                add("--output")
                add(out.absolutePath)
                add("--no-desugaring")
                add("--no-minification")
                add("--pg-conf")
                add(optimize.absolutePath)
                add("--pg-conf")
                add(keep.absolutePath)
                add("--pg-conf-output")
                add(collective.absolutePath)
                bootClasspath.files.filter { it.isFile }.forEach { lib ->
                    add("--lib")
                    add(lib.absolutePath)
                }
                // Class-file mode has to resolve invokedynamic (Kotlin lambdas). android.jar
                // does not contain java.lang.invoke, so the compile JDK is a second --lib.
                add("--lib")
                add(File(System.getProperty("java.home")).absolutePath)
                classpath.forEach { jar ->
                    add("--classpath")
                    add(jar.absolutePath)
                }
                program.forEach { add(it.absolutePath) }
            }
        check(args.none { it.contains("proguard-featured") || it.contains("-assumevalues") }) {
            "r8UnfoldedMarker must not pass featured rules or -assumevalues."
        }
        val argFile = work.resolve("r8-args.txt")
        argFile.writeText(args.joinToString("\n"))

        execOperations.javaexec {
            classpath(r8Classpath)
            mainClass.set("com.android.tools.r8.R8")
            maxHeapSize = "2g"
            args("@${argFile.absolutePath}")
        }

        val kept = containsClass(out, MARKER_ENTRY)
        logger.lifecycle(
            "r8UnfoldedMarker wrote ${out.absolutePath}; NewCheckoutDisabledBranch present=$kept",
        )
    }

    private fun asClassContainer(
        file: File,
        dest: File,
    ): File? {
        if (!file.exists()) return null
        val name = file.name.lowercase()
        if (name.endsWith(".apk") || name.endsWith(".aab")) return null
        if (file.isDirectory) {
            val nested = File(file, "classes.jar")
            if (nested.isFile) return nested
            val jarred =
                dest.resolve(
                    "dir-${file.absolutePath.hashCode().toUInt().toString(16)}.jar",
                )
            return jarClassFiles(file, jarred)
        }
        if (!file.isFile) return null
        if (name.endsWith(".jar")) return file
        return extractClassesJar(file, dest)
    }

    private fun jarClassFiles(
        dir: File,
        dest: File,
    ): File? {
        if (!dir.isDirectory) return null
        val classes =
            dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        if (classes.isEmpty()) return null
        ZipOutputStream(dest.outputStream()).use { zip ->
            classes.forEach { classFile ->
                val name =
                    dir
                        .toPath()
                        .relativize(classFile.toPath())
                        .toString()
                        .replace('\\', '/')
                zip.putNextEntry(ZipEntry(name))
                classFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return dest
    }

    private fun extractClassesJar(
        archive: File,
        dest: File,
    ): File? =
        try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry("classes.jar") ?: return null
                val out = dest.resolve("${archive.nameWithoutExtension}-classes.jar")
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                out
            }
        } catch (_: ZipException) {
            null
        } catch (_: IOException) {
            null
        }

    private fun containsClass(
        file: File,
        entry: String,
    ): Boolean {
        if (!file.exists()) return false
        if (file.isDirectory) return File(file, entry).isFile
        return try {
            ZipFile(file).use { zip -> zip.getEntry(entry) != null }
        } catch (_: ZipException) {
            false
        } catch (_: IOException) {
            false
        }
    }

    private companion object {
        const val MARKER_ENTRY =
            "dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class"
        const val BRIDGE_ENTRY =
            "dev/androidbroadcast/featured/sample/checkout/CheckoutFlagObserversKt.class"
    }
}
