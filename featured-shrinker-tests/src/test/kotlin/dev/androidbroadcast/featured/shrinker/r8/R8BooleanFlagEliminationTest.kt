package dev.androidbroadcast.featured.shrinker.r8

import dev.androidbroadcast.featured.shrinker.assertions.assertClassAbsent
import dev.androidbroadcast.featured.shrinker.assertions.assertClassDoesNotReference
import dev.androidbroadcast.featured.shrinker.assertions.assertClassPresent
import dev.androidbroadcast.featured.shrinker.bytecode.BIFURCATED_CALLER_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.ELSE_BRANCH_CODE_INTERNAL
import dev.androidbroadcast.featured.shrinker.bytecode.IF_BRANCH_CODE_INTERNAL
import dev.androidbroadcast.featured.shrinker.harness.R8TestHarness
import dev.androidbroadcast.featured.shrinker.rules.writeBooleanRules
import dev.androidbroadcast.featured.shrinker.rules.writeBooleanRulesWithKeptDeadBranch
import dev.androidbroadcast.featured.shrinker.rules.writeNoBooleanAssumeRules
import kotlin.test.Test

/**
 * Verifies that boolean local flags declared via the Gradle DSL generate `-assumevalues`
 * ProGuard/R8 rules that cause R8 to dead-code-eliminate all code reachable only through
 * the disabled branch of a flag check.
 *
 * Strategy: use ASM to build synthetic bytecode that mirrors the plugin-generated structure,
 * write rules files in the exact format `ProguardRulesGenerator` produces, run R8
 * programmatically, and assert presence/absence of flag-guarded classes in the output JAR.
 *
 * ### Boolean flag — bifurcated caller design
 *
 * ```java
 * // Mirrors dev.androidbroadcast.featured.ConfigValues
 * class ConfigValues { boolean enabled; ConfigValues(boolean) }
 *
 * // Mirrors ExtensionFunctionGenerator output for module ":test"
 * class GeneratedFlagExtensionsTestKt {
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
 *         if (GeneratedFlagExtensionsTestKt.isDarkModeEnabled(cv)) {
 *             new IfBranchCode().doWork();
 *         } else {
 *             new ElseBranchCode().doWork();
 *         }
 *     }
 * }
 * ```
 */
internal class R8BooleanFlagEliminationTest : R8TestHarness() {
    /**
     * With `return false`, `isDarkModeEnabled` is pinned to `false` at R8 time.
     * The if-branch (`IfBranchCode`) becomes unreachable and must be eliminated;
     * the else-branch (`ElseBranchCode`) is the only live path and must survive.
     */
    @Test
    fun `if-branch class is eliminated when boolean flag returns false`() {
        val outputJar = runBooleanR8 { writeBooleanRules(it, returnValue = false) }

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
        val outputJar = runBooleanR8 { writeBooleanRules(it, returnValue = true) }

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
        val outputJar = runBooleanR8 { writeNoBooleanAssumeRules(it) }

        assertClassPresent(outputJar, IF_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, ELSE_BRANCH_CODE_INTERNAL)
    }

    /**
     * Regression guard for the consumer pitfall: a user-supplied `-keep` on a class that is
     * only reachable through the disabled branch defeats dead-code elimination.
     *
     * The `-assumevalues … return false` rule is present and still works — R8 constant-folds
     * the flag and drops the dead branch's call site, so runtime behaviour is unchanged. But
     * `-keep class IfBranchCode { *; }` is an unconditional GC root, so the class itself can no
     * longer be tree-shaken: it survives in the output even though nothing reaches it.
     *
     * This is the failure mode behind broad wildcard / `@Keep` rules silently inflating the
     * APK. The control test above proves elimination normally happens; this test proves a
     * `-keep` is what brings the dead class back.
     *
     * The final assertion pins the documented split between the two R8 phases: even though the
     * class is kept, the `-assumevalues` rule must still have folded the disabled branch, so
     * `BifurcatedCaller` must no longer reference `IfBranchCode`. Without this, the test would
     * pass even if folding silently stopped working and the call site stayed reachable.
     */
    @Test
    fun `dead-branch class survives when a user -keep rule pins it despite the assumevalues rule`() {
        val outputJar = runBooleanR8 { writeBooleanRulesWithKeptDeadBranch(it) }

        assertClassPresent(outputJar, IF_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, ELSE_BRANCH_CODE_INTERNAL)
        assertClassPresent(outputJar, BIFURCATED_CALLER_INTERNAL)
        assertClassDoesNotReference(outputJar, BIFURCATED_CALLER_INTERNAL, IF_BRANCH_CODE_INTERNAL)
    }
}
