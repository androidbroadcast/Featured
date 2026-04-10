package dev.androidbroadcast.featured.shrinker.bytecode

import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal fun putClass(
    jos: JarOutputStream,
    internalName: String,
    bytes: ByteArray,
) {
    jos.putNextEntry(JarEntry("$internalName.class"))
    jos.write(bytes)
    jos.closeEntry()
}

/**
 * Assembles a JAR containing the synthetic boolean-flag class hierarchy:
 * `ConfigValues`, the boolean extensions class, `IfBranchCode`, `ElseBranchCode`,
 * and `BifurcatedCaller`.
 */
internal fun buildBooleanInputJar(dest: File) {
    JarOutputStream(dest.outputStream()).use { jos ->
        putClass(jos, CONFIG_VALUES_INTERNAL, booleanConfigValuesBytes())
        putClass(jos, BOOL_EXTENSIONS_INTERNAL, booleanExtensionsBytes())
        putClass(jos, IF_BRANCH_CODE_INTERNAL, sideEffectClassBytes(IF_BRANCH_CODE_INTERNAL))
        putClass(jos, ELSE_BRANCH_CODE_INTERNAL, sideEffectClassBytes(ELSE_BRANCH_CODE_INTERNAL))
        putClass(jos, BIFURCATED_CALLER_INTERNAL, bifurcatedCallerBytes())
    }
}

/**
 * Assembles a JAR containing the synthetic int-flag class hierarchy:
 * `IntConfigValues`, the int extensions class, `PositiveCountCode`, and `IntCaller`.
 */
internal fun buildIntInputJar(dest: File) {
    JarOutputStream(dest.outputStream()).use { jos ->
        putClass(jos, INT_CONFIG_VALUES_INTERNAL, intConfigValuesBytes())
        putClass(jos, INT_EXTENSIONS_INTERNAL, intExtensionsBytes())
        putClass(jos, POSITIVE_COUNT_CODE_INTERNAL, sideEffectClassBytes(POSITIVE_COUNT_CODE_INTERNAL))
        putClass(jos, INT_CALLER_INTERNAL, intCallerBytes())
    }
}
