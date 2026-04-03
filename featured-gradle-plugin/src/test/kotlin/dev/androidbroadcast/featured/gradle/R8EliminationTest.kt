package dev.androidbroadcast.featured.gradle

import com.android.tools.r8.JdkClassFileProvider
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import org.junit.After
import org.junit.Before
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GETSTATIC
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
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the library's core guarantee: local flags declared via the Gradle DSL generate
 * `-assumevalues` ProGuard/R8 rules that cause R8 to dead-code-eliminate all code reachable
 * only when the flag is enabled.
 *
 * Strategy: use ASM to build synthetic bytecode that mirrors the plugin-generated structure,
 * write a rules file in the exact format [ProguardRulesGenerator] produces, run R8
 * programmatically, and assert presence / absence of the flag-guarded class in the output.
 *
 * ### Synthetic class design
 *
 * ```
 * // Mirrors dev.androidbroadcast.featured.ConfigValues
 * class ConfigValues { boolean enabled; ConfigValues(boolean enabled) { ... } }
 *
 * // Mirrors ExtensionFunctionGenerator output for module ":test"
 * class FeaturedTest_FlagExtensionsKt {
 *     static boolean isDarkModeEnabled(ConfigValues cv) { return cv.enabled; }
 * }
 *
 * // Code that must be absent when the flag is off
 * class BehindFlagCode {
 *     public static int sideEffect;   // kept by rule so R8 cannot eliminate the write
 *     void doWork() { sideEffect++; }
 * }
 *
 * // Entry point — public method with boolean parameter (unknown value at R8 time)
 * class Caller {
 *     static void execute(boolean enabled) {
 *         ConfigValues cv = new ConfigValues(enabled);
 *         if (FeaturedTest_FlagExtensionsKt.isDarkModeEnabled(cv)) {
 *             new BehindFlagCode().doWork();
 *         }
 *     }
 * }
 * ```
 *
 * Because `execute` is public and kept, R8 cannot infer the value of `enabled`.
 * Therefore `isDarkModeEnabled` has an unknown return value **unless** the
 * `-assumevalues` rule overrides it.
 */
internal class R8EliminationTest {
    private lateinit var workDir: File

    @Before
    fun setup() {
        workDir = Files.createTempDirectory("r8-elimination-test").toFile()
    }

    @After
    fun cleanup() {
        workDir.deleteRecursively()
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * With `-assumevalues … return false`, R8 treats the flag as permanently disabled.
     * The true-branch is dead code, [BEHIND_FLAG_CODE_INTERNAL] becomes unreachable,
     * and R8 must eliminate it from the output.
     */
    @Test
    fun `class behind disabled local flag is eliminated by R8`() {
        val inputJar = workDir.resolve("input.jar").also { buildInputJar(it) }
        val rulesFile = workDir.resolve("rules-false.pro").also { writeRulesFile(it) }
        val outputJar = workDir.resolve("output-false.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassPresent(outputJar, CALLER_INTERNAL)
        assertClassAbsent(outputJar, BEHIND_FLAG_CODE_INTERNAL)
    }

    /**
     * Without any `-assumevalues` rule R8 cannot determine the return value of
     * `isDarkModeEnabled` (it depends on an unknown boolean parameter). Both branches
     * are potentially reachable, so [BEHIND_FLAG_CODE_INTERNAL] must survive R8.
     *
     * Together with the first test this proves that dead-code elimination is caused
     * specifically by the generated rule, not by R8's own constant-folding.
     */
    @Test
    fun `class behind flag survives R8 when no assumevalues rule is present`() {
        val inputJar = workDir.resolve("input.jar").also { buildInputJar(it) }
        val rulesFile = workDir.resolve("rules-no-assume.pro").also { writeRulesFileWithoutAssume(it) }
        val outputJar = workDir.resolve("output-no-assume.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassPresent(outputJar, CALLER_INTERNAL)
        assertClassPresent(outputJar, BEHIND_FLAG_CODE_INTERNAL)
    }

    // ── Synthetic bytecode ────────────────────────────────────────────────────

    private fun buildInputJar(dest: File) {
        JarOutputStream(dest.outputStream()).use { jos ->
            putClass(jos, CONFIG_VALUES_INTERNAL, configValuesBytes())
            putClass(jos, EXTENSIONS_INTERNAL, extensionsBytes())
            putClass(jos, BEHIND_FLAG_CODE_INTERNAL, behindFlagCodeBytes())
            putClass(jos, CALLER_INTERNAL, callerBytes())
        }
    }

    private fun putClass(
        jos: JarOutputStream,
        internalName: String,
        bytes: ByteArray,
    ) {
        jos.putNextEntry(JarEntry("$internalName.class"))
        jos.write(bytes)
        jos.closeEntry()
    }

    /**
     * `class ConfigValues { boolean enabled; ConfigValues(boolean) }`
     *
     * The constructor parameter makes the field value unknown to R8 when `Caller.execute`
     * forwards its own unknown parameter: `new ConfigValues(enabled)`.
     */
    private fun configValuesBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, CONFIG_VALUES_INTERNAL, null, OBJECT, null)
                visitField(ACC_PUBLIC, "enabled", "Z", null, null).visitEnd()
                visitMethod(ACC_PUBLIC, "<init>", "(Z)V", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false)
                    visitVarInsn(ALOAD, 0)
                    visitVarInsn(ILOAD, 1)
                    visitFieldInsn(PUTFIELD, CONFIG_VALUES_INTERNAL, "enabled", "Z")
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    /**
     * Mirrors [ExtensionFunctionGenerator]'s output for module `":test"`:
     * `static boolean isDarkModeEnabled(ConfigValues cv) { return cv.enabled; }`
     *
     * Reading an instance field whose value derives from an unknown parameter is
     * something R8 cannot constant-fold — exactly like the real extension function
     * that reads from `ConfigValues` at runtime.  The `-assumevalues` rule
     * overrides this return value to a build-time constant.
     */
    private fun extensionsBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, EXTENSIONS_INTERNAL, null, OBJECT, null)
                visitMethod(ACC_PUBLIC or ACC_STATIC, IS_DARK_MODE_ENABLED, "(L$CONFIG_VALUES_INTERNAL;)Z", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitFieldInsn(GETFIELD, CONFIG_VALUES_INTERNAL, "enabled", "Z")
                    visitInsn(IRETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    /**
     * Code that must be absent when the flag is disabled.
     *
     * `doWork()` writes to a public static field so R8 cannot treat the call as a
     * no-op and remove the instantiation when the branch is live.
     */
    private fun behindFlagCodeBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, BEHIND_FLAG_CODE_INTERNAL, null, OBJECT, null)
                visitField(ACC_PUBLIC or ACC_STATIC, "sideEffect", "I", null, 0).visitEnd()
                visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false)
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitMethod(ACC_PUBLIC, "doWork", "()V", null, null).apply {
                    visitCode()
                    visitFieldInsn(GETSTATIC, BEHIND_FLAG_CODE_INTERNAL, "sideEffect", "I")
                    visitInsn(ICONST_1)
                    visitInsn(IADD)
                    visitFieldInsn(PUTSTATIC, BEHIND_FLAG_CODE_INTERNAL, "sideEffect", "I")
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    /**
     * Entry point: `static void execute(boolean enabled)`.
     *
     * The boolean parameter is unknown at R8 time because `execute` is public and kept.
     * That makes `isDarkModeEnabled`'s return value unknown — unless overridden by
     * `-assumevalues`.
     */
    private fun callerBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, CALLER_INTERNAL, null, OBJECT, null)
                visitMethod(ACC_PUBLIC or ACC_STATIC, "execute", "(Z)V", null, null).apply {
                    visitCode()
                    visitTypeInsn(NEW, CONFIG_VALUES_INTERNAL)
                    visitInsn(DUP)
                    visitVarInsn(ILOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, CONFIG_VALUES_INTERNAL, "<init>", "(Z)V", false)
                    visitVarInsn(ASTORE, 1)
                    visitVarInsn(ALOAD, 1)
                    visitMethodInsn(INVOKESTATIC, EXTENSIONS_INTERNAL, IS_DARK_MODE_ENABLED, "(L$CONFIG_VALUES_INTERNAL;)Z", false)
                    val skipLabel = Label()
                    visitJumpInsn(IFEQ, skipLabel)
                    visitTypeInsn(NEW, BEHIND_FLAG_CODE_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, BEHIND_FLAG_CODE_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, BEHIND_FLAG_CODE_INTERNAL, "doWork", "()V", false)
                    visitLabel(skipLabel)
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    // ── ProGuard rules ────────────────────────────────────────────────────────

    /**
     * Approximates the output of [ProguardRulesGenerator.generate] for module `":test"`,
     * Boolean flag `"dark_mode"` with `defaultValue = false`. The `-keep` and `-dontwarn`
     * directives are test scaffolding, not generator output.
     */
    private fun writeRulesFile(dest: File) {
        dest.writeText(
            """
            -assumevalues class $EXTENSIONS_FQN {
                boolean $IS_DARK_MODE_ENABLED($CONFIG_VALUES_FQN) return false;
            }
            -keep class $CALLER_FQN { *; }
            -dontwarn **
            """.trimIndent(),
        )
    }

    /**
     * Rules without any `-assumevalues` block.
     * [BehindFlagCode.sideEffect] is kept so R8 cannot treat `doWork()` as a no-op
     * when the branch is live (unknown flag value).
     */
    private fun writeRulesFileWithoutAssume(dest: File) {
        dest.writeText(
            """
            -keep class $CALLER_FQN { *; }
            -keepclassmembers class $BEHIND_FLAG_CODE_FQN { public static int sideEffect; }
            -dontwarn **
            """.trimIndent(),
        )
    }

    // ── R8 invocation ─────────────────────────────────────────────────────────

    private fun runR8(
        inputJar: File,
        rulesFile: File,
        outputJar: File,
    ) {
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
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    private fun assertClassAbsent(
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

    private fun assertClassPresent(
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

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/ConfigValues"
        const val BEHIND_FLAG_CODE_INTERNAL = "BehindFlagCode"
        const val CALLER_INTERNAL = "Caller"

        val EXTENSIONS_INTERNAL =
            "dev/androidbroadcast/featured/generated/${ExtensionFunctionGenerator.jvmFileName(":test")}"

        const val CONFIG_VALUES_FQN = "dev.androidbroadcast.featured.ConfigValues"
        const val CALLER_FQN = "Caller"
        const val BEHIND_FLAG_CODE_FQN = "BehindFlagCode"
        val EXTENSIONS_FQN = EXTENSIONS_INTERNAL.replace('/', '.')

        const val IS_DARK_MODE_ENABLED = "isDarkModeEnabled"
        const val OBJECT = "java/lang/Object"
    }
}

/**
 * [ClassWriter] with [COMPUTE_FRAMES] that returns `java/lang/Object` as the common
 * supertype for any pair. Avoids ClassLoader-based lookups for synthetic test classes
 * that are not on the JVM classpath.
 */
private fun safeClassWriter(): ClassWriter =
    object : ClassWriter(COMPUTE_FRAMES) {
        override fun getCommonSuperClass(
            type1: String,
            type2: String,
        ): String = "java/lang/Object"
    }
