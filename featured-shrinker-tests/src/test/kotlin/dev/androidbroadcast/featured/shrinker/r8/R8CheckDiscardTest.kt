package dev.androidbroadcast.featured.shrinker.r8

import com.android.tools.r8.CompilationFailedException
import dev.androidbroadcast.featured.shrinker.assertions.assertClassAbsent
import dev.androidbroadcast.featured.shrinker.bytecode.IF_BRANCH_CODE_INTERNAL
import dev.androidbroadcast.featured.shrinker.harness.R8TestHarness
import dev.androidbroadcast.featured.shrinker.rules.writeBooleanRulesWithCheckDiscard
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Verifies the verification half of the flag dead-code-elimination story: the `-checkdiscard`
 * rules emitted by `CheckDiscardRulesGenerator` actually make R8 fail the build when
 * flag-guarded code is *not* removed.
 *
 * Unlike unit tests (which see the full pre-R8 classpath) and `androidTest` (assembled without
 * minification), `-checkdiscard` inspects the optimized output, so it is the only thing that can
 * catch a forgotten reference keeping a "disabled" feature alive. These tests run real R8 over
 * the same synthetic bytecode used by [R8BooleanFlagEliminationTest] and assert both outcomes.
 */
internal class R8CheckDiscardTest : R8TestHarness() {
    /**
     * `-assumevalues … return false` makes `IfBranchCode` unreachable and eliminated, so the
     * `-checkdiscard class IfBranchCode { *; }` assertion holds and R8 completes successfully.
     */
    @Test
    fun `checkdiscard passes and build succeeds when the disabled branch class is eliminated`() {
        val outputJar = runBooleanR8 { writeBooleanRulesWithCheckDiscard(it, assume = false) }

        assertClassAbsent(outputJar, IF_BRANCH_CODE_INTERNAL)
    }

    /**
     * `-assumevalues … return true` keeps `IfBranchCode` reachable, so it survives R8. The
     * `-checkdiscard` assertion on it then fails and R8 aborts with `Discard checks failed`,
     * surfacing as a [CompilationFailedException]. This is the build-time guarantee: a flag that
     * fails to strip its guarded code breaks the build instead of shipping dead/live code.
     */
    @Test
    fun `checkdiscard fails the build when a checked class survives`() {
        assertFailsWith<CompilationFailedException> {
            runBooleanR8 { writeBooleanRulesWithCheckDiscard(it, assume = true) }
        }
    }
}
