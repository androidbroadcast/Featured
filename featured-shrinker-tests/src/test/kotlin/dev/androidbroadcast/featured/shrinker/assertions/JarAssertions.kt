package dev.androidbroadcast.featured.shrinker.assertions

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.JarFile
import kotlin.test.assertFalse
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

/**
 * Asserts that the bytecode of [ownerInternalName] in [jar] contains no reference to
 * [referencedInternalName] — neither as a `NEW`/type reference nor as a method-call target.
 *
 * This proves that R8 actually constant-folded the flag and removed the dead branch's call
 * site from the *caller*, as opposed to merely keeping the branch-target class alive. A test
 * that only checked class presence would still pass if folding silently stopped working and
 * the call site stayed reachable; this assertion closes that gap.
 */
internal fun assertClassDoesNotReference(
    jar: File,
    ownerInternalName: String,
    referencedInternalName: String,
) {
    val classBytes =
        JarFile(jar).use { jf ->
            val entry =
                assertNotNull(
                    jf.getJarEntry("$ownerInternalName.class"),
                    "Expected $ownerInternalName to be present in the output JAR",
                )
            jf.getInputStream(entry).use { it.readBytes() }
        }

    var referenced = false
    val referencedType = "L$referencedInternalName;"
    val detector =
        object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor =
                object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitTypeInsn(
                        opcode: Int,
                        type: String?,
                    ) {
                        if (type == referencedInternalName) referenced = true
                    }

                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String?,
                        name: String?,
                        descriptor: String?,
                        isInterface: Boolean,
                    ) {
                        if (owner == referencedInternalName) referenced = true
                    }

                    override fun visitFieldInsn(
                        opcode: Int,
                        owner: String?,
                        name: String?,
                        descriptor: String?,
                    ) {
                        if (owner == referencedInternalName) referenced = true
                    }
                }

            override fun visitField(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                value: Any?,
            ): FieldVisitor? {
                if (descriptor == referencedType) referenced = true
                return null
            }
        }
    ClassReader(classBytes).accept(detector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

    assertFalse(
        referenced,
        "Expected $ownerInternalName to no longer reference $referencedInternalName after R8 " +
            "folded the disabled branch, but a reference was found in its bytecode",
    )
}
