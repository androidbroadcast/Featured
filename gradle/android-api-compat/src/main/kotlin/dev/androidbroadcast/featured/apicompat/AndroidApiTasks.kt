package dev.androidbroadcast.featured.apicompat

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal abstract class DumpAndroidApiTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ListProperty<Directory>

    @get:Classpath
    abstract val kmpClasses: ConfigurableFileCollection

    @get:Classpath
    abstract val toolsClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val dumpFile: RegularFileProperty

    @TaskAction
    fun dump() {
        val inputs =
            JvmAbiDumper.collectClassInputs(classJars.get().map { it.asFile }) +
                JvmAbiDumper.collectClassInputs(classDirectories.get().map { it.asFile }) +
                JvmAbiDumper.collectClassInputs(kmpClasses.files)
        if (inputs.isEmpty()) {
            throw GradleException(
                "${modulePath.get()} Android API dump has no class inputs. " +
                    "The Android compilation was not wired.",
            )
        }
        val text = JvmAbiDumper.dump(inputs, toolsClasspath.files.map { it.toPath() })
        val output = dumpFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(text, Charsets.UTF_8)
    }
}

@DisableCachingByDefault(because = "The check fails instead of producing an artifact, and must not write the baseline.")
internal abstract class CheckAndroidApiTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedDump: RegularFileProperty

    /**
     * Optional so a missing baseline is reported by the check action instead of being rewritten.
     * The action only reads this file.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baseline: RegularFileProperty

    @TaskAction
    fun check() {
        val baselineFile = baseline.get().asFile
        val generated = generatedDump.get().asFile.readText(Charsets.UTF_8)
        val result = AndroidApiCheck.check(baselineFile, generated)
        if (result is AndroidApiCheck.Result.Failed) {
            throw GradleException(
                "Android API check failed for ${modulePath.get()}. ${result.message}\n" +
                    "Update the baseline on purpose with ${modulePath.get()}:updateAndroidApi. " +
                    "CI does not run that task.",
            )
        }
    }
}

@DisableCachingByDefault(because = "Writes a source-tree baseline only when the task is invoked on purpose.")
internal abstract class UpdateAndroidApiTask : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedDump: RegularFileProperty

    @get:OutputFile
    abstract val baseline: RegularFileProperty

    @TaskAction
    fun update() {
        val dump = generatedDump.get().asFile
        val text = dump.readText(Charsets.UTF_8)
        if (!AndroidApiCheck.hasDeclaration(text)) {
            throw GradleException(
                "${modulePath.get()} Android API dump is empty or has no declaration. " +
                    "updateAndroidApi did not write ${baseline.get().asFile.path}.",
            )
        }
        val destination = baseline.get().asFile
        destination.parentFile?.mkdirs()
        destination.writeBytes(dump.readBytes())
    }
}
