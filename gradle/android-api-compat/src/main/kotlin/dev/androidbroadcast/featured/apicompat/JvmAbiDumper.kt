package dev.androidbroadcast.featured.apicompat

import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.abi.AbiValidationToolchain.Companion.abiValidation
import org.jetbrains.kotlin.buildtools.api.abi.dumpJvmAbiToStringOperation
import java.nio.file.Path

/**
 * Dumps public and protected JVM signatures with Kotlin 2.4.20's ABI toolchain.
 * The default operation is used: no include-internal flag is set.
 * Directories are expanded to class files because the operation accepts class files and jars only.
 */
@OptIn(ExperimentalBuildToolsApi::class)
internal object JvmAbiDumper {
    private val lock = Any()
    private val toolchainsByClasspath = HashMap<List<String>, KotlinToolchains>()

    fun dump(
        classFiles: List<Path>,
        toolsClasspath: List<Path>,
    ): String {
        if (classFiles.isEmpty()) {
            return ""
        }
        if (toolsClasspath.isEmpty()) {
            error("kotlin-build-tools-impl classpath is empty")
        }
        synchronized(lock) {
            val toolchains = toolchainsFor(toolsClasspath)
            return toolchains.createBuildSession().use { session ->
                val dump = StringBuilder()
                val operation =
                    session.kotlinToolchains.abiValidation.dumpJvmAbiToStringOperation(
                        dump,
                        classFiles,
                    )
                session.executeOperation(operation)
                dump.toString()
            }
        }
    }

    fun collectClassInputs(files: Iterable<java.io.File>): List<Path> {
        val result = LinkedHashSet<Path>()
        files.forEach { file ->
            if (!file.exists()) {
                return@forEach
            }
            if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile }.forEach { addClassOrJar(it, result) }
            } else {
                addClassOrJar(file, result)
            }
        }
        return result.toList()
    }

    private fun addClassOrJar(
        file: java.io.File,
        result: MutableSet<Path>,
    ) {
        if (!file.isFile) {
            return
        }
        val name = file.name
        if (name.endsWith(".class") || name.endsWith(".jar")) {
            result.add(file.toPath())
        }
    }

    private fun toolchainsFor(classpath: List<Path>): KotlinToolchains {
        val key = classpath.map { it.toAbsolutePath().normalize().toString() }
        return toolchainsByClasspath.getOrPut(key) {
            KotlinToolchains.loadImplementation(classpath)
        }
    }
}
