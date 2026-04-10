package dev.androidbroadcast.featured.shrinker.bytecode

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
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IFEQ
import org.objectweb.asm.Opcodes.IFLE
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

internal const val OBJECT = "java/lang/Object"

// Boolean flag — class names (JVM internal form)
internal const val CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/ConfigValues"
internal const val IF_BRANCH_CODE_INTERNAL = "IfBranchCode"
internal const val ELSE_BRANCH_CODE_INTERNAL = "ElseBranchCode"
internal const val BIFURCATED_CALLER_INTERNAL = "BifurcatedCaller"

// Derived from ExtensionFunctionGenerator.jvmFileName(":test"):
// ":test".removePrefix(":") = "test" → capitalize → "Test" → "FeaturedTest_FlagExtensionsKt"
internal const val BOOL_EXTENSIONS_INTERNAL =
    "dev/androidbroadcast/featured/generated/FeaturedTest_FlagExtensionsKt"

internal const val IS_DARK_MODE_ENABLED = "isDarkModeEnabled"

// Int flag — class names (JVM internal form)
internal const val INT_CONFIG_VALUES_INTERNAL = "dev/androidbroadcast/featured/IntConfigValues"
internal const val POSITIVE_COUNT_CODE_INTERNAL = "PositiveCountCode"
internal const val INT_CALLER_INTERNAL = "IntCaller"

// Derived from ExtensionFunctionGenerator.jvmFileName(":int-test"):
// ":int-test".removePrefix(":") = "int-test" → capitalize first char → "Int-test" → "FeaturedInt-test_FlagExtensionsKt"
internal const val INT_EXTENSIONS_INTERNAL =
    "dev/androidbroadcast/featured/generated/FeaturedInt-test_FlagExtensionsKt"

internal const val GET_MAX_RETRIES = "getMaxRetries"

/**
 * `class ConfigValues { boolean enabled; ConfigValues(boolean) }`
 *
 * The constructor parameter makes the field value unknown to R8 when `BifurcatedCaller`
 * forwards its own unknown `enabled` parameter: `new ConfigValues(enabled)`.
 */
internal fun booleanConfigValuesBytes(): ByteArray =
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
 * Mirrors `ExtensionFunctionGenerator`'s output for module `":test"`:
 * `static boolean isDarkModeEnabled(ConfigValues cv) { return cv.enabled; }`
 *
 * The `-assumevalues` rule overrides this return value to a build-time constant.
 */
internal fun booleanExtensionsBytes(): ByteArray =
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
 * A single input JAR covers all Boolean scenarios:
 * `flag=false/true` × `if/else` branch elimination.
 */
internal fun bifurcatedCallerBytes(): ByteArray =
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

/**
 * `class IntConfigValues { int count; IntConfigValues(int) }`
 */
internal fun intConfigValuesBytes(): ByteArray =
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
 * Mirrors `ExtensionFunctionGenerator`'s output for module `":int-test"`:
 * `static int getMaxRetries(IntConfigValues cv) { return cv.count; }`
 */
internal fun intExtensionsBytes(): ByteArray =
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
internal fun intCallerBytes(): ByteArray =
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

/**
 * Builds a class with:
 * - `public static int sideEffect` — keeps R8 from treating `doWork()` as a no-op
 * - `public void doWork()` — increments `sideEffect`
 *
 * Used for all branch-target classes so they share the same structure.
 */
internal fun sideEffectClassBytes(internalName: String): ByteArray =
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

/**
 * [ClassWriter] with [COMPUTE_FRAMES] that returns `java/lang/Object` as the common
 * supertype for any pair. Avoids ClassLoader-based lookups for synthetic test classes
 * that are not on the JVM classpath.
 */
internal fun safeClassWriter(): ClassWriter =
    object : ClassWriter(COMPUTE_FRAMES) {
        override fun getCommonSuperClass(
            type1: String,
            type2: String,
        ): String = "java/lang/Object"
    }
