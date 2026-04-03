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
import org.objectweb.asm.Opcodes.*
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
 * `-assumevalues` ProGuard/R8 rules that cause R8 to dead-code-eliminate all code that is
 * reachable only through the disabled branch of a flag check.
 *
 * Strategy: use ASM to build synthetic bytecode that mirrors the plugin-generated structure,
 * write rules files in the exact format [ProguardRulesGenerator] produces, run R8
 * programmatically, and assert presence / absence of flag-guarded classes in the output JAR.
 *
 * ### Boolean flag — bifurcated caller design
 *
 * ```java
 * // Mirrors dev.androidbroadcast.featured.ConfigValues
 * class ConfigValues { boolean enabled; ConfigValues(boolean) }
 *
 * // Mirrors ExtensionFunctionGenerator output for module ":test"
 * class FeaturedTest_FlagExtensionsKt {
 *     static boolean isDarkModeEnabled(ConfigValues cv) { return cv.enabled; }
 * }
 *
 * // Code that must be absent when the flag is disabled (if-branch)
 * class IfBranchCode { static int sideEffect; void doWork() { sideEffect++; } }
 *
 * // Code that must be absent when the flag is enabled (else-branch)
 * class ElseBranchCode { static int sideEffect; void doWork() { sideEffect++; } }
 *
 * // Entry point kept by -keep; unknown boolean parameter prevents R8 from
 * // constant-folding the flag value without an -assumevalues rule.
 * class BifurcatedCaller {
 *     static void execute(boolean enabled) {
 *         ConfigValues cv = new ConfigValues(enabled);
 *         if (FeaturedTest_FlagExtensionsKt.isDarkModeEnabled(cv)) {
 *             new IfBranchCode().doWork();
 *         } else {
 *             new ElseBranchCode().doWork();
 *         }
 *     }
 * }
 * ```
 *
 * ### Int flag — positive-guard caller design
 *
 * ```java
 * class IntConfigValues { int count; IntConfigValues(int) }
 *
 * class FeaturedIntTest_FlagExtensionsKt {
 *     static int getMaxRetries(IntConfigValues cv) { return cv.count; }
 * }
 *
 * class PositiveCountCode { static int sideEffect; void doWork() { sideEffect++; } }
 *
 * class IntCaller {
 *     static void execute(int count) {
 *         IntConfigValues cv = new IntConfigValues(count);
 *         if (FeaturedIntTest_FlagExtensionsKt.getMaxRetries(cv) > 0) {
 *             new PositiveCountCode().doWork();
 *         }
 *     }
 * }
 * ```
 *
 * When `-assumevalues` pins `getMaxRetries` to `0`, R8 constant-folds `0 > 0` to `false`
 * and eliminates the if-branch entirely.
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

    // ── Boolean flag — elimination tests ──────────────────────────────────────

    /**
     * With `return false`, `isDarkModeEnabled` is pinned to `false` at R8 time.
     * The if-branch (`IfBranchCode`) becomes unreachable and must be eliminated;
     * the else-branch (`ElseBranchCode`) is the only live path and must survive.
     */
    @Test
    fun `if-branch class is eliminated when boolean flag returns false`() {
        val inputJar = workDir.resolve("input.jar").also { buildBooleanInputJar(it) }
        val rulesFile = workDir.resolve("rules.pro").also { writeBooleanRules(it, returnValue = false) }
        val outputJar = workDir.resolve("output.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassAbsent(outputJar, IF_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, ELSE_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, BIFURCATED_CALLER_INTERNAL)
    }

    /**
     * With `return true`, `isDarkModeEnabled` is pinned to `true` at R8 time.
     * The else-branch (`ElseBranchCode`) becomes unreachable and must be eliminated;
     * the if-branch (`IfBranchCode`) is the only live path and must survive.
     */
    @Test
    fun `else-branch class is eliminated when boolean flag returns true`() {
        val inputJar = workDir.resolve("input.jar").also { buildBooleanInputJar(it) }
        val rulesFile = workDir.resolve("rules.pro").also { writeBooleanRules(it, returnValue = true) }
        val outputJar = workDir.resolve("output.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassPresent(outputJar, IF_BRANCH_CODE_INTERNAL)
        assertClassAbsent(outputJar, ELSE_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, BIFURCATED_CALLER_INTERNAL)
    }

    /**
     * Without any `-assumevalues` rule R8 cannot determine the return value of
     * `isDarkModeEnabled` (it depends on the unknown `enabled` parameter). Both branches
     * are potentially reachable, so both `IfBranchCode` and `ElseBranchCode` must survive.
     *
     * Together with the two tests above this proves that dead-code elimination is caused
     * specifically by the generated rule, not by R8's own constant-folding.
     */
    @Test
    fun `both branch classes survive when no boolean assumevalues rule is present`() {
        val inputJar = workDir.resolve("input.jar").also { buildBooleanInputJar(it) }
        val rulesFile = workDir.resolve("rules.pro").also { writeNoBooleanAssumeRules(it) }
        val outputJar = workDir.resolve("output.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassPresent(outputJar, IF_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, ELSE_BRANCH_CODE_INTERNAL)
    }

    // ── Int flag — elimination tests ──────────────────────────────────────────

    /**
     * With `return 0`, `getMaxRetries` is pinned to `0`. R8 constant-folds `0 > 0` to
     * `false`, making the if-branch dead code. `PositiveCountCode` must be eliminated.
     */
    @Test
    fun `guarded class is eliminated when int flag is assumed to return zero`() {
        val inputJar = workDir.resolve("input.jar").also { buildIntInputJar(it) }
        val rulesFile = workDir.resolve("rules.pro").also { writeIntRules(it, returnValue = 0) }
        val outputJar = workDir.resolve("output.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassAbsent(outputJar, POSITIVE_COUNT_CODE_INTERNAL)
        assertClassPresent(outputJar, INT_CALLER_INTERNAL)
    }

    /**
     * Without `-assumevalues` R8 cannot determine `getMaxRetries`'s return value.
     * The if-branch is potentially reachable so `PositiveCountCode` must survive.
     */
    @Test
    fun `guarded class survives when int flag has no assumevalues rule`() {
        val inputJar = workDir.resolve("input.jar").also { buildIntInputJar(it) }
        val rulesFile = workDir.resolve("rules.pro").also { writeNoIntAssumeRules(it) }
        val outputJar = workDir.resolve("output.jar")

        runR8(inputJar, rulesFile, outputJar)

        assertClassPresent(outputJar, POSITIVE_COUNT_CODE_INTERNAL)
        assertClassPresent(outputJar, INT_CALLER_INTERNAL)
    }

    // ── Boolean bytecode builders ─────────────────────────────────────────────

    private fun buildBooleanInputJar(dest: File) {
        JarOutputStream(dest.outputStream()).use { jos ->
            putClass(jos, CONFIG_VALUES_INTERNAL, booleanConfigValuesBytes())
            putClass(jos, BOOL_EXTENSIONS_INTERNAL, booleanExtensionsBytes())
            putClass(jos, IF_BRANCH_CODE_INTERNAL, sideEffectClassBytes(IF_BRANCH_CODE_INTERNAL))
            putClass(jos, ELSE_BRANCH_CODE_INTERNAL, sideEffectClassBytes(ELSE_BRANCH_CODE_INTERNAL))
            putClass(jos, BIFURCATED_CALLER_INTERNAL, bifurcatedCallerBytes())
        }
    }

    /**
     * `class ConfigValues { boolean enabled; ConfigValues(boolean) }`
     *
     * The constructor parameter makes the field value unknown to R8 when `BifurcatedCaller`
     * forwards its own unknown `enabled` parameter: `new ConfigValues(enabled)`.
     */
    private fun booleanConfigValuesBytes(): ByteArray =
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
     * The `-assumevalues` rule overrides this return value to a build-time constant.
     */
    private fun booleanExtensionsBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, BOOL_EXTENSIONS_INTERNAL, null, OBJECT, null)
                visitMethod(
                    ACC_PUBLIC or ACC_STATIC,
                    IS_DARK_MODE_ENABLED,
                    "(L$CONFIG_VALUES_INTERNAL;)Z",
                    null,
                    null,
                ).apply {
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
     * Entry point with both if and else branches:
     *
     * ```java
     * static void execute(boolean enabled) {
     *     ConfigValues cv = new ConfigValues(enabled);
     *     if (BoolExtensions.isDarkModeEnabled(cv)) {
     *         new IfBranchCode().doWork();
     *     } else {
     *         new ElseBranchCode().doWork();
     *     }
     * }
     * ```
     *
     * A single input JAR covers all four Boolean scenarios:
     * `flag=false/true` × `if/else` branch elimination.
     */
    private fun bifurcatedCallerBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, BIFURCATED_CALLER_INTERNAL, null, OBJECT, null)
                visitMethod(ACC_PUBLIC or ACC_STATIC, "execute", "(Z)V", null, null).apply {
                    visitCode()
                    visitTypeInsn(NEW, CONFIG_VALUES_INTERNAL)
                    visitInsn(DUP)
                    visitVarInsn(ILOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, CONFIG_VALUES_INTERNAL, "<init>", "(Z)V", false)
                    visitVarInsn(ASTORE, 1)
                    visitVarInsn(ALOAD, 1)
                    visitMethodInsn(
                        INVOKESTATIC,
                        BOOL_EXTENSIONS_INTERNAL,
                        IS_DARK_MODE_ENABLED,
                        "(L$CONFIG_VALUES_INTERNAL;)Z",
                        false,
                    )
                    val elseLabel = Label()
                    val endLabel = Label()
                    visitJumpInsn(IFEQ, elseLabel)
                    // if-branch
                    visitTypeInsn(NEW, IF_BRANCH_CODE_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, IF_BRANCH_CODE_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, IF_BRANCH_CODE_INTERNAL, "doWork", "()V", false)
                    visitJumpInsn(GOTO, endLabel)
                    // else-branch
                    visitLabel(elseLabel)
                    visitTypeInsn(NEW, ELSE_BRANCH_CODE_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, ELSE_BRANCH_CODE_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, ELSE_BRANCH_CODE_INTERNAL, "doWork", "()V", false)
                    visitLabel(endLabel)
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    // ── Int bytecode builders ─────────────────────────────────────────────────

    private fun buildIntInputJar(dest: File) {
        JarOutputStream(dest.outputStream()).use { jos ->
            putClass(jos, INT_CONFIG_VALUES_INTERNAL, intConfigValuesBytes())
            putClass(jos, INT_EXTENSIONS_INTERNAL, intExtensionsBytes())
            putClass(jos, POSITIVE_COUNT_CODE_INTERNAL, sideEffectClassBytes(POSITIVE_COUNT_CODE_INTERNAL))
            putClass(jos, INT_CALLER_INTERNAL, intCallerBytes())
        }
    }

    /**
     * `class IntConfigValues { int count; IntConfigValues(int) }`
     */
    private fun intConfigValuesBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, INT_CONFIG_VALUES_INTERNAL, null, OBJECT, null)
                visitField(ACC_PUBLIC, "count", "I", null, null).visitEnd()
                visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false)
                    visitVarInsn(ALOAD, 0)
                    visitVarInsn(ILOAD, 1)
                    visitFieldInsn(PUTFIELD, INT_CONFIG_VALUES_INTERNAL, "count", "I")
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    /**
     * Mirrors [ExtensionFunctionGenerator]'s output for module `":int-test"`:
     * `static int getMaxRetries(IntConfigValues cv) { return cv.count; }`
     */
    private fun intExtensionsBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, INT_EXTENSIONS_INTERNAL, null, OBJECT, null)
                visitMethod(
                    ACC_PUBLIC or ACC_STATIC,
                    GET_MAX_RETRIES,
                    "(L$INT_CONFIG_VALUES_INTERNAL;)I",
                    null,
                    null,
                ).apply {
                    visitCode()
                    visitVarInsn(ALOAD, 0)
                    visitFieldInsn(GETFIELD, INT_CONFIG_VALUES_INTERNAL, "count", "I")
                    visitInsn(IRETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    /**
     * ```java
     * static void execute(int count) {
     *     IntConfigValues cv = new IntConfigValues(count);
     *     if (IntExtensions.getMaxRetries(cv) > 0) {
     *         new PositiveCountCode().doWork();
     *     }
     * }
     * ```
     *
     * `IFLE` (jump if ≤ 0) is the branch that skips the block when count is zero.
     * With `-assumevalues return 0`, R8 folds `0 > 0` to `false` and eliminates the block.
     */
    private fun intCallerBytes(): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, INT_CALLER_INTERNAL, null, OBJECT, null)
                visitMethod(ACC_PUBLIC or ACC_STATIC, "execute", "(I)V", null, null).apply {
                    visitCode()
                    visitTypeInsn(NEW, INT_CONFIG_VALUES_INTERNAL)
                    visitInsn(DUP)
                    visitVarInsn(ILOAD, 0)
                    visitMethodInsn(INVOKESPECIAL, INT_CONFIG_VALUES_INTERNAL, "<init>", "(I)V", false)
                    visitVarInsn(ASTORE, 1)
                    visitVarInsn(ALOAD, 1)
                    visitMethodInsn(
                        INVOKESTATIC,
                        INT_EXTENSIONS_INTERNAL,
                        GET_MAX_RETRIES,
                        "(L$INT_CONFIG_VALUES_INTERNAL;)I",
                        false,
                    )
                    val skipLabel = Label()
                    visitJumpInsn(IFLE, skipLabel)
                    visitTypeInsn(NEW, POSITIVE_COUNT_CODE_INTERNAL)
                    visitInsn(DUP)
                    visitMethodInsn(INVOKESPECIAL, POSITIVE_COUNT_CODE_INTERNAL, "<init>", "()V", false)
                    visitMethodInsn(INVOKEVIRTUAL, POSITIVE_COUNT_CODE_INTERNAL, "doWork", "()V", false)
                    visitLabel(skipLabel)
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    // ── Shared bytecode builder ───────────────────────────────────────────────

    /**
     * Builds a class with:
     * - `public static int sideEffect` — keeps R8 from treating `doWork()` as a no-op
     * - `public void doWork()` — increments `sideEffect`
     *
     * Used for all branch-target classes so they share the same structure.
     */
    private fun sideEffectClassBytes(internalName: String): ByteArray =
        safeClassWriter()
            .apply {
                visit(V1_8, ACC_PUBLIC, internalName, null, OBJECT, null)
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
                    visitFieldInsn(GETSTATIC, internalName, "sideEffect", "I")
                    visitInsn(ICONST_1)
                    visitInsn(IADD)
                    visitFieldInsn(PUTSTATIC, internalName, "sideEffect", "I")
                    visitInsn(RETURN)
                    visitMaxs(0, 0)
                    visitEnd()
                }
                visitEnd()
            }.toByteArray()

    private fun putClass(
        jos: JarOutputStream,
        internalName: String,
        bytes: ByteArray,
    ) {
        jos.putNextEntry(JarEntry("$internalName.class"))
        jos.write(bytes)
        jos.closeEntry()
    }

    // ── ProGuard rules ────────────────────────────────────────────────────────

    /**
     * Approximates [ProguardRulesGenerator] output for a Boolean flag `"dark_mode"` in
     * module `":test"`. The `-keep` and `-dontwarn` directives are test scaffolding only.
     *
     * `-keepclassmembers` pins the `sideEffect` field of the **surviving** branch class so
     * that R8 cannot treat the `doWork()` call as a no-op and eliminate the class via
     * write-only field optimisation. The dead branch class intentionally has no such rule,
     * so R8 is free to eliminate it once the branch becomes unreachable.
     */
    private fun writeBooleanRules(
        dest: File,
        returnValue: Boolean,
    ) {
        val survivingClass = if (returnValue) IF_BRANCH_CODE_FQN else ELSE_BRANCH_CODE_FQN
        dest.writeText(
            """
            -assumevalues class $BOOL_EXTENSIONS_FQN {
                boolean $IS_DARK_MODE_ENABLED($CONFIG_VALUES_FQN) return $returnValue;
            }
            -keep class $BIFURCATED_CALLER_FQN { *; }
            -keepclassmembers class $survivingClass { public static int sideEffect; }
            -dontwarn **
            """.trimIndent(),
        )
    }

    /**
     * No `-assumevalues` block — R8 cannot constant-fold the flag value.
     * The `-keepclassmembers` rules ensure the `sideEffect` field is not stripped
     * while the branch-target classes remain alive via reachability from the kept caller.
     */
    private fun writeNoBooleanAssumeRules(dest: File) {
        dest.writeText(
            """
            -keep class $BIFURCATED_CALLER_FQN { *; }
            -keepclassmembers class $IF_BRANCH_CODE_FQN { public static int sideEffect; }
            -keepclassmembers class $ELSE_BRANCH_CODE_FQN { public static int sideEffect; }
            -dontwarn **
            """.trimIndent(),
        )
    }

    /**
     * Approximates [ProguardRulesGenerator] output for an Int flag `"max_retries"` in
     * module `":int-test"` with the given [returnValue].
     */
    private fun writeIntRules(
        dest: File,
        returnValue: Int,
    ) {
        dest.writeText(
            """
            -assumevalues class $INT_EXTENSIONS_FQN {
                int $GET_MAX_RETRIES($INT_CONFIG_VALUES_FQN) return $returnValue;
            }
            -keep class $INT_CALLER_FQN { *; }
            -dontwarn **
            """.trimIndent(),
        )
    }

    private fun writeNoIntAssumeRules(dest: File) {
        dest.writeText(
            """
            -keep class $INT_CALLER_FQN { *; }
            -keepclassmembers class $POSITIVE_COUNT_CODE_FQN { public static int sideEffect; }
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
        // Boolean flag — class names (JVM internal form)
        const val CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/ConfigValues"
        const val IF_BRANCH_CODE_INTERNAL = "IfBranchCode"
        const val ELSE_BRANCH_CODE_INTERNAL = "ElseBranchCode"
        const val BIFURCATED_CALLER_INTERNAL = "BifurcatedCaller"

        val BOOL_EXTENSIONS_INTERNAL =
            "dev/androidbroadcast/featured/generated/${ExtensionFunctionGenerator.jvmFileName(":test")}"

        // Boolean flag — FQN form for ProGuard rules
        const val CONFIG_VALUES_FQN = "dev.androidbroadcast.featured.ConfigValues"
        const val IF_BRANCH_CODE_FQN = "IfBranchCode"
        const val ELSE_BRANCH_CODE_FQN = "ElseBranchCode"
        const val BIFURCATED_CALLER_FQN = "BifurcatedCaller"
        val BOOL_EXTENSIONS_FQN = BOOL_EXTENSIONS_INTERNAL.replace('/', '.')

        const val IS_DARK_MODE_ENABLED = "isDarkModeEnabled"

        // Int flag — class names (JVM internal form)
        const val INT_CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/IntConfigValues"
        const val POSITIVE_COUNT_CODE_INTERNAL = "PositiveCountCode"
        const val INT_CALLER_INTERNAL = "IntCaller"

        val INT_EXTENSIONS_INTERNAL =
            "dev/androidbroadcast/featured/generated/${ExtensionFunctionGenerator.jvmFileName(":int-test")}"

        // Int flag — FQN form for ProGuard rules
        const val INT_CONFIG_VALUES_FQN = "dev.androidbroadcast.featured.IntConfigValues"
        const val POSITIVE_COUNT_CODE_FQN = "PositiveCountCode"
        const val INT_CALLER_FQN = "IntCaller"
        val INT_EXTENSIONS_FQN = INT_EXTENSIONS_INTERNAL.replace('/', '.')

        const val GET_MAX_RETRIES = "getMaxRetries"

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
