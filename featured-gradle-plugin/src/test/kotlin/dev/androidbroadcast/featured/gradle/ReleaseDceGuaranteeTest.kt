package dev.androidbroadcast.featured.gradle

import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import dev.androidbroadcast.featured.gradle.manifest.androidSdkDirOrNull
import dev.androidbroadcast.featured.gradle.manifest.copyManifestFixture
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
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
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Closes the release-stripping loop the synthetic shrinker tests leave open.
 *
 * The Android fixture compiles the plugin-generated accessor. Its stub boolean is a native
 * call, so R8 cannot fold it. A class reached only through that stub must survive. A class
 * reached only through the generated accessor must disappear, which is what shows the
 * `-assumevalues` rule — not the stub — did the stripping. `discard()` fails the build if
 * that class is kept. A second R8 run uses the compiled method's real JVM name.
 */
class ReleaseDceGuaranteeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `release build strips the discarded class and the compiled accessor matches the rule`() {
        val projectDir = prepareFixture()
        val result =
            gradleRunner(projectDir)
                .withArguments("assembleRelease", "--stacktrace")
                .build()
        assertTrue(
            result.output.contains("BUILD SUCCESSFUL"),
            "assembleRelease should succeed when discard() holds\n${result.output}",
        )

        val rules = projectDir.resolve("build/featured/proguard-featured.pro").readText()
        val accessor = parseAssumeValues(rules).single { it.methodName == "isDarkModeEnabled" }
        val classFile = findClassFile(projectDir, accessor.classInternalName)
        assertMethodPresent(classFile.readBytes(), accessor)

        val apk = findReleaseApk(projectDir)
        val mapping = projectDir.resolve("build/outputs/mapping/release/mapping.txt")
        val mappingText = if (mapping.isFile) mapping.readText() else "<no mapping>"
        assertFalse(
            mappingText.contains("dev.androidbroadcast.featured.testapp.DeadFeature ->"),
            "DeadFeature must be absent from the release mapping\n${mappingText.take(1500)}",
        )
        assertTrue(
            mappingText.contains("dev.androidbroadcast.featured.testapp.LiveFeature ->"),
            "LiveFeature must survive so the check is not 'R8 deleted everything'\n${mappingText.take(1500)}",
        )
        assertTrue(
            mappingText.contains("dev.androidbroadcast.featured.testapp.UnruledFeature ->"),
            "UnruledFeature is reached only through the opaque stub. If it is gone, " +
                "R8 folded the stub and DeadFeature's absence does not prove -assumevalues.\n" +
                mappingText.take(1500),
        )
        assertFalse(dexContains(apk, DEAD_FEATURE_INTERNAL), "DeadFeature must be absent from the release APK")
        assertTrue(dexContains(apk, LIVE_FEATURE_INTERNAL), "LiveFeature must remain in the release APK")
        assertTrue(
            dexContains(apk, UNRULED_FEATURE_INTERNAL),
            "UnruledFeature must remain. Its absence would mean the stub was folded, not the plugin rule.",
        )

        val folded = runR8OnCompiledAccessor(classFile.readBytes(), accessor, includeAssumeValues = true)
        assertFalse(jarContains(folded, IF_ONLY_INTERNAL))
        assertTrue(jarContains(folded, ELSE_ONLY_INTERNAL))

        val unfolded = runR8OnCompiledAccessor(classFile.readBytes(), accessor, includeAssumeValues = false)
        assertTrue(jarContains(unfolded, IF_ONLY_INTERNAL))
        assertTrue(jarContains(unfolded, ELSE_ONLY_INTERNAL))
    }

    @Test
    fun `release build fails when a discarded class is kept`() {
        val projectDir = prepareFixture()
        projectDir.resolve("proguard-rules.pro").appendText(
            "\n-keep class $DEAD_FEATURE_FQN { *; }\n",
        )

        val failure =
            assertFailsWith<UnexpectedBuildFailure> {
                gradleRunner(projectDir)
                    .withArguments("assembleRelease", "--stacktrace")
                    .build()
            }
        assertDiscardFailed(failure)
    }

    @Test
    fun `dropping assumevalues leaves the discarded class reachable`() {
        val projectDir = prepareFixture()
        val failure = failedRelease(projectDir, "-Pfeatured.dce.mutateRules=drop-assumevalues")
        assertDiscardFailed(failure)
        val rules = generatedRules(projectDir)
        assertFalse(
            rules.contains("-assumevalues"),
            "The mutation did not remove -assumevalues\n$rules",
        )
    }

    @Test
    fun `renaming the assumevalues method misses the compiled accessor`() {
        val projectDir = prepareFixture()
        val failure = failedRelease(projectDir, "-Pfeatured.dce.mutateRules=rename-method")
        assertDiscardFailed(failure)

        val rules = generatedRules(projectDir)
        val mutated = parseAssumeValues(rules).single()
        assertEquals("isDarkModeEnabledMissing", mutated.methodName)
        val compiled =
            AssumeValuesRule(
                className = mutated.className,
                methodName = "isDarkModeEnabled",
                parameterType = mutated.parameterType,
            )
        assertMethodPresent(findClassFile(projectDir, compiled.classInternalName).readBytes(), compiled)
        assertFalse(
            classHasMethod(findClassFile(projectDir, mutated.classInternalName).readBytes(), mutated),
            "Renamed rule must not match the compiled accessor",
        )
    }

    @Test
    fun `constant stub does not strip the discarded class without assumevalues`() {
        val projectDir = prepareFixture()
        val stub =
            projectDir.resolve(
                "src/main/kotlin/dev/androidbroadcast/featured/FeaturedRuntimeStub.kt",
            )
        val rewritten =
            stub.readText().replace(
                "getValueCached(param: ConfigParam<T>): ConfigValue<T> = opaqueValue(param)",
                "getValueCached(param: ConfigParam<T>): ConfigValue<T> = ConfigValue(param.defaultValue)",
            )
        assertTrue(
            rewritten.contains("= ConfigValue(param.defaultValue)"),
            "Could not restore the constant stub",
        )
        stub.writeText(rewritten)

        // A stub that returns the declared default must not be enough. If this build starts
        // succeeding, R8 folded the accessor by itself and the happy path no longer proves the rule.
        val failure = failedRelease(projectDir, "-Pfeatured.dce.mutateRules=drop-assumevalues")
        assertDiscardFailed(failure)
    }

    private fun failedRelease(
        projectDir: File,
        vararg extraArgs: String,
    ): UnexpectedBuildFailure =
        assertFailsWith {
            gradleRunner(projectDir)
                .withArguments(listOf("assembleRelease", "--stacktrace") + extraArgs)
                .build()
        }

    private fun assertDiscardFailed(failure: UnexpectedBuildFailure) {
        val output = failure.buildResult.output
        assertTrue(
            failure.message.orEmpty().contains("Discard checks failed") ||
                output.contains("Discard checks failed"),
            "Release R8 must fail when the discarded class is still reachable\n$output",
        )
    }

    private fun generatedRules(projectDir: File): String = projectDir.resolve("build/featured/proguard-featured.pro").readText()

    private fun releaseMapping(projectDir: File): String {
        val mapping = projectDir.resolve("build/outputs/mapping/release/mapping.txt")
        return if (mapping.isFile) mapping.readText() else "<no mapping>"
    }

    private fun mappingContains(
        mapping: String,
        className: String,
    ): Boolean = mapping.contains("$className ->")

    private fun classHasMethod(
        classBytes: ByteArray,
        rule: AssumeValuesRule,
    ): Boolean {
        var found = false
        ClassReader(classBytes).accept(
            object : ClassVisitor(ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if ("$name$descriptor" == rule.jvmMethod) found = true
                    return null
                }
            },
            0,
        )
        return found
    }

    private fun prepareFixture(): File {
        val sdkDir = androidSdkDirOrNull()
        assumeTrue(
            "ANDROID_HOME or ANDROID_SDK_ROOT must be set to run the release DCE test",
            sdkDir != null,
        )
        val projectDir = tempFolder.newFolder("android-dce-project")
        copyManifestFixture("android-dce-project", projectDir)
        projectDir.resolve("local.properties").writeText("sdk.dir=${sdkDir!!.invariantSeparatorsPath}\n")
        return projectDir
    }

    private fun gradleRunner(projectDir: File): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .forwardOutput()

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
            "Compiled accessor is missing ${rule.jvmMethod}. Rule would be a silent no-op. Methods: $methods",
        )
    }

    /**
     * Runs R8 on the compiler's accessor class. The method body is replaced with a read of an
     * unknown field so constant-folding of the fixture stub cannot impersonate `-assumevalues`.
     * The method name and descriptor stay exactly as compiled.
     */
    private fun runR8OnCompiledAccessor(
        compiledAccessor: ByteArray,
        rule: AssumeValuesRule,
        includeAssumeValues: Boolean,
    ): File {
        val inputJar = tempFolder.newFile("r8-input-$includeAssumeValues.jar")
        val outputJar = tempFolder.newFile("r8-output-$includeAssumeValues.jar")
        val rulesFile = tempFolder.newFile("r8-rules-$includeAssumeValues.pro")
        writeJar(
            inputJar,
            rule.classInternalName to rewriteAccessorBody(compiledAccessor, rule),
            CONFIG_VALUES_INTERNAL to configValuesBytes(),
            CALLER_INTERNAL to callerBytes(rule),
            IF_ONLY_INTERNAL to sideEffectBytes(IF_ONLY_INTERNAL),
            ELSE_ONLY_INTERNAL to sideEffectBytes(ELSE_ONLY_INTERNAL),
        )
        rulesFile.writeText(r8Rules(rule, includeAssumeValues))
        R8.run(
            R8Command
                .builder()
                .addProgramFiles(inputJar.toPath())
                .addProguardConfigurationFiles(rulesFile.toPath())
                .addLibraryResourceProvider(JdkClassFileProvider.fromSystemJdk())
                .setOutput(outputJar.toPath(), OutputMode.ClassFile)
                .setDisableMinification(true)
                .build(),
        )
        return outputJar
    }

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

    private fun r8Rules(
        rule: AssumeValuesRule,
        includeAssumeValues: Boolean,
    ): String {
        val assume =
            if (includeAssumeValues) {
                """
                -assumevalues class ${rule.className} {
                    boolean ${rule.methodName}(${rule.parameterType}) return false;
                }
                """.trimIndent()
            } else {
                ""
            }
        return """
            $assume
            -keep class $CALLER_FQN { *; }
            -keepclassmembers class $IF_ONLY_INTERNAL { public static int hits; }
            -keepclassmembers class $ELSE_ONLY_INTERNAL { public static int hits; }
            -dontwarn **
            """.trimIndent()
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
                    val elseLabel = org.objectweb.asm.Label()
                    val endLabel = org.objectweb.asm.Label()
                    visitJumpInsn(IFEQ, elseLabel)
                    visitTypeInsn(NEW, IF_ONLY_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, IF_ONLY_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, IF_ONLY_INTERNAL, "run", "()V", false)
                    visitJumpInsn(GOTO, endLabel)
                    visitLabel(elseLabel)
                    visitTypeInsn(NEW, ELSE_ONLY_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, ELSE_ONLY_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, ELSE_ONLY_INTERNAL, "run", "()V", false)
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
        java.util.zip.ZipOutputStream(dest.outputStream()).use { zip ->
            classes.forEach { (internalName, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry("$internalName.class"))
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
                val methodName = match.groupValues[1]
                val parameterType = match.groupValues[2]
                AssumeValuesRule(
                    className = className,
                    methodName = methodName,
                    parameterType = parameterType,
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

    private fun findReleaseApk(projectDir: File): File =
        projectDir
            .resolve("build/outputs/apk")
            .walkTopDown()
            .filter { it.isFile && it.extension == "apk" && "release" in it.path }
            .firstOrNull()
            ?: error("Release APK was not produced under ${projectDir.resolve("build/outputs/apk")}")

    private fun dexContains(
        apk: File,
        internalName: String,
    ): Boolean =
        ZipFile(apk).use { zip ->
            zip
                .entries()
                .asSequence()
                .filter { it.name.endsWith(".dex") }
                .any { entry ->
                    zip
                        .getInputStream(entry)
                        .use { it.readBytes() }
                        .toString(Charsets.ISO_8859_1)
                        .contains(internalName)
                }
        }

    private fun jarContains(
        jar: File,
        internalName: String,
    ): Boolean =
        ZipFile(jar).use { zip ->
            zip.getEntry("$internalName.class") != null
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
        const val CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/ConfigValues"
        const val DEAD_FEATURE_FQN = "dev.androidbroadcast.featured.testapp.DeadFeature"
        const val DEAD_FEATURE_INTERNAL = "dev/androidbroadcast/featured/testapp/DeadFeature"
        const val LIVE_FEATURE_INTERNAL = "dev/androidbroadcast/featured/testapp/LiveFeature"
        const val UNRULED_FEATURE_INTERNAL = "dev/androidbroadcast/featured/testapp/UnruledFeature"
        const val CALLER_INTERNAL = "DceCaller"
        const val CALLER_FQN = "DceCaller"
        const val IF_ONLY_INTERNAL = "IfOnly"
        const val ELSE_ONLY_INTERNAL = "ElseOnly"
    }
}
