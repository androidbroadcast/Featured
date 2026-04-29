package dev.androidbroadcast.featured.shrinker.rules

import dev.androidbroadcast.featured.shrinker.bytecode.BIFURCATED_CALLER_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.BOOL_EXTENSIONS_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.CONFIG_VALUES_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.ELSE_BRANCH_CODE_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.GET_MAX_RETRIES
import dev.androidbroadcast.featured.shrinker.bytecode.IF_BRANCH_CODE_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.INT_CALLER_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.INT_CONFIG_VALUES_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.INT_EXTENSIONS_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.IS_DARK_MODE_ENABLED
import dev.androidbroadcast.featured.shrinker.bytecode.POSITIVE_COUNT_CODE_INTERNAL
import java.io.File

private val BIFURCATED_CALLER_FQN = BIFURCATED_CALLER_INTERNAL.replace('/', '.')
private val BOOL_EXTENSIONS_FQN = BOOL_EXTENSIONS_INTERNAL.replace('/', '.')
private val CONFIG_VALUES_FQN = CONFIG_VALUES_INTERNAL.replace('/', '.')
private val ELSE_BRANCH_CODE_FQN = ELSE_BRANCH_CODE_INTERNAL.replace('/', '.')
private val IF_BRANCH_CODE_FQN = IF_BRANCH_CODE_INTERNAL.replace('/', '.')
private val INT_CALLER_FQN = INT_CALLER_INTERNAL.replace('/', '.')
private val INT_CONFIG_VALUES_FQN = INT_CONFIG_VALUES_INTERNAL.replace('/', '.')
private val INT_EXTENSIONS_FQN = INT_EXTENSIONS_INTERNAL.replace('/', '.')
private val POSITIVE_COUNT_CODE_FQN = POSITIVE_COUNT_CODE_INTERNAL.replace('/', '.')

/**
 * Approximates `ProguardRulesGenerator` output for a Boolean flag `"dark_mode"` in
 * module `":test"`. The `-keep` and `-dontwarn` directives are test scaffolding only.
 *
 * `-keepclassmembers` pins the `sideEffect` field of the **surviving** branch class so
 * that R8 cannot treat the `doWork()` call as a no-op and eliminate the class via
 * write-only field optimisation. The dead branch class intentionally has no such rule,
 * so R8 is free to eliminate it once the branch becomes unreachable.
 */
internal fun writeBooleanRules(
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
internal fun writeNoBooleanAssumeRules(dest: File) {
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
 * Approximates `ProguardRulesGenerator` output for an Int flag `"max_retries"` in
 * module `":int-test"` with the given [returnValue].
 */
internal fun writeIntRules(
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

internal fun writeNoIntAssumeRules(dest: File) {
    dest.writeText(
        """
        -keep class $INT_CALLER_FQN { *; }
        -keepclassmembers class $POSITIVE_COUNT_CODE_FQN { public static int sideEffect; }
        -dontwarn **
        """.trimIndent(),
    )
}
