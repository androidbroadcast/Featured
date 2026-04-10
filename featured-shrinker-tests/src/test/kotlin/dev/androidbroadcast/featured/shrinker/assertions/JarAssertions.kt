package dev.androidbroadcast.featured.shrinker.assertions

import java.io.File
import java.util.jar.JarFile
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal fun assertClassAbsent(
    jar: File,
    internalName: String,
) {
    JarFile(jar).use { jf ->
        assertNull(
            jf.getJarEntry("$internalName.class"),
            "Expected $internalName to be dead-code-eliminated by R8 but it was found in the output JAR",
        )
    }
}

internal fun assertClassPresent(
    jar: File,
    internalName: String,
) {
    JarFile(jar).use { jf ->
        assertNotNull(
            jf.getJarEntry("$internalName.class"),
            "Expected $internalName to survive R8 but it was not found in the output JAR",
        )
    }
}
