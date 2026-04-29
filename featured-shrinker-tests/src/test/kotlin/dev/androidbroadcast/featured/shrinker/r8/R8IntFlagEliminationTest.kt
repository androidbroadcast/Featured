package dev.androidbroadcast.featured.shrinker.r8

import dev.androidbroadcast.featured.shrinker.assertions.assertClassAbsent
import dev.androidbroadcast.featured.shrinker.assertions.assertClassPresent
import dev.androidbroadcast.featured.shrinker.bytecode.INT_CALLER_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.POSITIVE_COUNT_CODE_INTERNAL
import dev.androidbroadcast.featured.shrinker.harness.R8TestHarness
import dev.androidbroadcast.featured.shrinker.rules.writeIntRules
import dev.androidbroadcast.featured.shrinker.rules.writeNoIntAssumeRules
import kotlin.test.Test

/**
 * Verifies that int local flags declared via the Gradle DSL generate `-assumevalues`
 * ProGuard/R8 rules that cause R8 to dead-code-eliminate code guarded by an int comparison.
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
internal class R8IntFlagEliminationTest : R8TestHarness() {
    /**
     * With `return 0`, `getMaxRetries` is pinned to `0`. R8 constant-folds `0 > 0` to
     * `false`, making the if-branch dead code. `PositiveCountCode` must be eliminated.
     */
    @Test
    fun `guarded class is eliminated when int flag is assumed to return zero`() {
        val outputJar = runIntR8 { writeIntRules(it, returnValue = 0) }

        assertClassAbsent(outputJar, POSITIVE_COUNT_CODE_INTERNAL)
        assertClassPresent(outputJar, INT_CALLER_INTERNAL)
    }

    /**
     * Without `-assumevalues` R8 cannot determine `getMaxRetries`'s return value.
     * The if-branch is potentially reachable so `PositiveCountCode` must survive.
     */
    @Test
    fun `guarded class survives when int flag has no assumevalues rule`() {
        val outputJar = runIntR8 { writeNoIntAssumeRules(it) }

        assertClassPresent(outputJar, POSITIVE_COUNT_CODE_INTERNAL)
        assertClassPresent(outputJar, INT_CALLER_INTERNAL)
    }
}
