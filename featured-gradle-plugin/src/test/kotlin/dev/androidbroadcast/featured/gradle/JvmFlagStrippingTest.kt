package dev.androidbroadcast.featured.gradle

import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import dev.androidbroadcast.featured.gradle.manifest.copyManifestFixture
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Opcodes.V1_8
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM closed loop for a local boolean with `default = false`.
 *
 * The fixture compiles the plugin-generated accessor. R8 is then given that compiled method
 * and `build/featured/proguard-featured.pro` exactly as the plugin wrote it. The accessor body
 * is rewritten to a read of an unknown field first, because the constant stub
 * `ConfigValue(param.defaultValue)` lets R8 delete the branch with no `-assumevalues` rule.
 * [ReleaseDceGuaranteeTest] is the Android loop. It does not cover this pass.
 */
class JvmFlagStrippingTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `generated rule strips the disabled branch only when that file is applied`() {
        val projectDir = tempFolder.newFolder("jvm-dce-project")
        copyManifestFixture("jvm-dce-project", projectDir)

        val result =
            gradleRunner(projectDir)
                .withArguments(
                    ":generateFeaturedProguardRules",
                    ":compileKotlin",
                    "--stacktrace",
                    "--no-build-cache",
                ).build()
        assertTaskSucceeded(result.task(":generateFeaturedProguardRules")?.outcome, ":generateFeaturedProguardRules")
        assertTaskSucceeded(result.task(":compileKotlin")?.outcome, ":compileKotlin")

        val rulesFile = projectDir.resolve("build/featured/proguard-featured.pro")
        assertTrue(rulesFile.isFile, "Plugin did not write ${rulesFile.path}")
        val accessor = parseAssumeValues(rulesFile.readText()).single()
        assertEquals(
            CONFIG_VALUES_FQN,
            accessor.parameterType,
            "The generated rule must name the compiled receiver, not a stand-in type",
        )
        val classFile = findClassFile(projectDir, accessor.classInternalName)
        val compiled = classFile.readBytes()
        assertMethodPresent(compiled, accessor)

        val rewritten = rewriteAccessorBody(compiled, accessor)
        assertOpaqueRewrite(rewritten, accessor)

        val inputJar = tempFolder.newFile("r8-input.jar")
        val keepFile = tempFolder.newFile("r8-keep.pro")
        val configValues = configValuesBytes()
        assertSyntheticConfigValues(configValues)
        writeJar(
            inputJar,
            accessor.classInternalName to rewritten,
            CONFIG_VALUES_INTERNAL to configValues,
            CALLER_INTERNAL to callerBytes(accessor),
            DISABLED_BRANCH_INTERNAL to sideEffectBytes(DISABLED_BRANCH_INTERNAL),
            LIVE_BRANCH_INTERNAL to sideEffectBytes(LIVE_BRANCH_INTERNAL),
        )
        keepFile.writeText(keepRules())

        val stripped = tempFolder.newFile("r8-with-generated-rule.jar")
        runR8(inputJar, stripped, keepFile, rulesFile)
        assertFalse(
            jarContains(stripped, DISABLED_BRANCH_INTERNAL),
            "Disabled branch must be absent when the generated file is applied. Entries: ${jarEntries(stripped)}",
        )
        assertTrue(
            jarContains(stripped, LIVE_BRANCH_INTERNAL),
            "Live branch must remain when the generated file is applied. Entries: ${jarEntries(stripped)}",
        )
        assertTrue(
            jarContains(stripped, CALLER_INTERNAL),
            "Caller must remain when the generated file is applied. Entries: ${jarEntries(stripped)}",
        )

        val unfolded = tempFolder.newFile("r8-without-generated-rule.jar")
        runR8(inputJar, unfolded, keepFile, generatedRules = null)
        assertTrue(
            jarContains(unfolded, DISABLED_BRANCH_INTERNAL),
            "Disabled branch must remain when the generated file is absent. Entries: ${jarEntries(unfolded)}",
        )
        assertTrue(
            jarContains(unfolded, LIVE_BRANCH_INTERNAL),
            "Live branch must remain when the generated file is absent. Entries: ${jarEntries(unfolded)}",
        )
    }

    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()

    private fun assertTaskSucceeded(
        outcome: TaskOutcome?,
        taskPath: String,
    ) {
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
            "Expected $taskPath to succeed, got $outcome",
        )
    }

    private fun assertMethodPresent(
        classBytes: ByteArray,
        rule: AssumeValuesRule,
    ) {
        val methods = mutableListOf<String>()
        ClassReader(classBytes).accept(
            object : ClassVisitor(ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    methods += "$name$descriptor"
                    return null
                }
            },
            0,
        )
        assertTrue(
            methods.contains(rule.jvmMethod),
            "Compiled accessor is missing ${rule.jvmMethod}. The generated rule would be a silent no-op. Methods: $methods",
        )
    }

    /**
     * The replacement reads an unknown field and keeps the compiled name and descriptor.
     * A no-op rewrite still calls `getValueCached` and must fail here, before R8.
     */
    private fun assertOpaqueRewrite(
        classBytes: ByteArray,
        rule: AssumeValuesRule,
    ) {
        var sawMethod = false
        var sawEnabledField = false
        var sawGetValueCached = false
        ClassReader(classBytes).accept(
            object : ClassVisitor(ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name != rule.methodName || descriptor != rule.descriptor) return null
                    sawMethod = true
                    return object : MethodVisitor(ASM9) {
                        override fun visitFieldInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                        ) {
                            if (
                                opcode == GETFIELD &&
                                owner == CONFIG_VALUES_INTERNAL &&
                                name == "enabled" &&
                                descriptor == "Z"
                            ) {
                                sawEnabledField = true
                            }
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String,
                            name: String,
                            descriptor: String,
                            isInterface: Boolean,
                        ) {
                            if (name == "getValueCached") sawGetValueCached = true
                        }
                    }
                }
            },
            0,
        )
        assertTrue(sawMethod, "Rewritten class has no ${rule.jvmMethod}")
        assertTrue(sawEnabledField, "Rewritten accessor must read ConfigValues.enabled. The rewrite was a no-op.")
        assertFalse(sawGetValueCached, "Rewritten accessor must not call getValueCached. The rewrite was a no-op.")
    }

    private fun assertSyntheticConfigValues(classBytes: ByteArray) {
        var hasEnabled = false
        var hasGetValueCached = false
        ClassReader(classBytes).accept(
            object : ClassVisitor(ASM9) {
                override fun visitField(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    value: Any?,
                ): FieldVisitor? {
                    if (name == "enabled" && descriptor == "Z") hasEnabled = true
                    return null
                }

                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name == "getValueCached") hasGetValueCached = true
                    return null
                }
            },
            0,
        )
        assertTrue(hasEnabled, "Synthetic ConfigValues must expose the unknown boolean field")
        assertFalse(
            hasGetValueCached,
            "Program jar must not contain the fixture ConfigValues. That stub can fold the flag without a rule.",
        )
    }

    private fun runR8(
        inputJar: File,
        outputJar: File,
        keepFile: File,
        generatedRules: File?,
    ) {
        val configurationFiles =
            if (generatedRules == null) {
                arrayOf(keepFile.toPath())
            } else {
                arrayOf(generatedRules.toPath(), keepFile.toPath())
            }
        R8.run(
            R8Command
                .builder()
                .addProgramFiles(inputJar.toPath())
                .addProguardConfigurationFiles(*configurationFiles)
                .addLibraryResourceProvider(JdkClassFileProvider.fromSystemJdk())
                .setOutput(outputJar.toPath(), OutputMode.ClassFile)
                .setDisableMinification(true)
                .build(),
        )
    }

    private fun keepRules(): String =
        """
        -keep class $CALLER_FQN { *; }
        -keepclassmembers class $DISABLED_BRANCH_INTERNAL { public static int hits; }
        -keepclassmembers class $LIVE_BRANCH_INTERNAL { public static int hits; }
        -dontwarn **
        """.trimIndent() + "\n"

    private fun rewriteAccessorBody(
        classBytes: ByteArray,
        rule: AssumeValuesRule,
    ): ByteArray {
        val reader = ClassReader(classBytes)
        val writer =
            object : ClassWriter(reader, 0) {
                override fun getCommonSuperClass(
                    type1: String,
                    type2: String,
                ): String = "java/lang/Object"
            }
        reader.accept(
            object : ClassVisitor(ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor {
                    val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
                    if (name != rule.methodName || descriptor != rule.descriptor) return delegate
                    return object : MethodVisitor(ASM9, null) {
                        override fun visitEnd() {
                            delegate.visitCode()
                            delegate.visitVarInsn(ALOAD, 0)
                            delegate.visitFieldInsn(GETFIELD, CONFIG_VALUES_INTERNAL, "enabled", "Z")
                            delegate.visitInsn(IRETURN)
                            delegate.visitMaxs(1, 1)
                            delegate.visitEnd()
                        }
                    }
                }
            },
            0,
        )
        return writer.toByteArray()
    }

    private fun configValuesBytes(): ByteArray =
        object : ClassWriter(0) {}
            .apply {
                visit(V1_8, ACC_PUBLIC, CONFIG_VALUES_INTERNAL, null, "java/lang/Object", null)
                visitField(ACC_PUBLIC, "enabled", "Z", null, null).visitEnd()
                visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                    visitInsn(RETURN)
                    visitMaxs(1, 1)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    private fun callerBytes(rule: AssumeValuesRule): ByteArray =
        object : ClassWriter(0) {}
            .apply {
                visit(V1_8, ACC_PUBLIC, CALLER_INTERNAL, null, "java/lang/Object", null)
                visitMethod(ACC_PUBLIC or ACC_STATIC, "execute", "(Z)V", null, null).apply {
                    visitCode()
                    visitTypeInsn(NEW, CONFIG_VALUES_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, CONFIG_VALUES_INTERNAL, "<init>", "()V", false)
                    visitInsn(DUP)
                    visitVarInsn(ILOAD, 0)
                    visitFieldInsn(PUTFIELD, CONFIG_VALUES_INTERNAL, "enabled", "Z")
                    visitVarInsn(ASTORE, 1)
                    visitVarInsn(ALOAD, 1)
                    visitMethodInsn(INVOKESTATIC, rule.classInternalName, rule.methodName, rule.descriptor, false)
                    val elseLabel = Label()
                    val endLabel = Label()
                    visitJumpInsn(IFEQ, elseLabel)
                    visitTypeInsn(NEW, DISABLED_BRANCH_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, DISABLED_BRANCH_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, DISABLED_BRANCH_INTERNAL, "run", "()V", false)
                    visitJumpInsn(GOTO, endLabel)
                    visitLabel(elseLabel)
                    visitTypeInsn(NEW, LIVE_BRANCH_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, LIVE_BRANCH_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, LIVE_BRANCH_INTERNAL, "run", "()V", false)
                    visitLabel(endLabel)
                    visitInsn(RETURN)
                    visitMaxs(3, 2)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    private fun sideEffectBytes(internalName: String): ByteArray =
        object : ClassWriter(0) {}
            .apply {
                visit(V1_8, ACC_PUBLIC, internalName, null, "java/lang/Object", null)
                visitField(ACC_PUBLIC or ACC_STATIC, "hits", "I", null, null).visitEnd()
                visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                    visitInsn(RETURN)
                    visitMaxs(1, 1)
                    visitEnd()
                }
                visitMethod(ACC_PUBLIC, "run", "()V", null, null).apply {
                    visitCode()
                    visitFieldInsn(GETSTATIC, internalName, "hits", "I")
                    visitInsn(ICONST_1)
                    visitInsn(IADD)
                    visitFieldInsn(PUTSTATIC, internalName, "hits", "I")
                    visitInsn(RETURN)
                    visitMaxs(2, 1)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    private fun writeJar(
        dest: File,
        vararg classes: Pair<String, ByteArray>,
    ) {
        ZipOutputStream(dest.outputStream()).use { zip ->
            classes.forEach { (internalName, bytes) ->
                zip.putNextEntry(ZipEntry("$internalName.class"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun parseAssumeValues(rules: String): List<AssumeValuesRule> {
        val className =
            Regex("""-assumevalues class ([\w.$]+) \{""")
                .find(rules)
                ?.groupValues
                ?.get(1)
                ?: error("Generated rules have no -assumevalues class:\n$rules")
        return Regex("""boolean (\w+)\(([\w.$]+)\) return false;""")
            .findAll(rules)
            .map { match ->
                AssumeValuesRule(
                    className = className,
                    methodName = match.groupValues[1],
                    parameterType = match.groupValues[2],
                )
            }.toList()
            .ifEmpty { error("No boolean assumevalues rule in:\n$rules") }
    }

    private fun findClassFile(
        projectDir: File,
        internalName: String,
    ): File =
        projectDir
            .resolve("build")
            .walkTopDown()
            .firstOrNull { it.isFile && it.path.replace('\\', '/').endsWith("$internalName.class") }
            ?: error("Compiled accessor $internalName.class was not found under ${projectDir.resolve("build")}")

    private fun jarContains(
        jar: File,
        internalName: String,
    ): Boolean = ZipFile(jar).use { zip -> zip.getEntry("$internalName.class") != null }

    private fun jarEntries(jar: File): String =
        ZipFile(jar).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .sorted()
                .joinToString()
        }

    private data class AssumeValuesRule(
        val className: String,
        val methodName: String,
        val parameterType: String,
    ) {
        val classInternalName: String = className.replace('.', '/')
        val descriptor: String = "(L${parameterType.replace('.', '/')};)Z"
        val jvmMethod: String = "$methodName$descriptor"
    }

    private companion object {
        const val CONFIG_VALUES_FQN = "dev.androidbroadcast.featured.ConfigValues"
        const val CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/ConfigValues"
        const val CALLER_INTERNAL = "DceCaller"
        const val CALLER_FQN = "DceCaller"
        const val DISABLED_BRANCH_INTERNAL = "DisabledBranch"
        const val LIVE_BRANCH_INTERNAL = "LiveBranch"
    }
}
