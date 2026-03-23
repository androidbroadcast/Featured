package dev.androidbroadcast.featured.detekt

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.lint
import io.gitlab.arturbosch.detekt.test.lintWithContext
import org.junit.Test
import kotlin.test.assertEquals

class UncheckedFlagAccessTest {
    private val rule = UncheckedFlagAccess()
    private val envWrapper = createEnvironment()
    private val env = envWrapper.env

    // ── No findings ──────────────────────────────────────────────────────────

    @Test
    fun `no finding for direct if check with bare flag reference`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host(newCheckout: Boolean) {
                if (newCheckout) { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for if check with array access pattern`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host(configValues: Map<Any, Boolean>, newCheckout: Any) {
                if (configValues[newCheckout] == true) { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for if check with dot-qualified flag reference`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            class Flags { val newCheckout: Boolean = false }

            fun host(featureFlags: Flags) {
                if (featureFlags.newCheckout) { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for when check with flag name in condition`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host(newCheckout: Boolean) {
                when {
                    newCheckout -> newCheckoutScreen()
                }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for call inside BehindFlag function same flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @BehindFlag("newCheckout")
            fun newCheckoutHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for call inside AssumesFlag function same flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            fun checkoutNavHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for call inside AssumesFlag class member function`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            class CheckoutContainer {
                fun render() { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag is silent with BindingContext empty`() {
        // Rule must not crash when run without type resolution
        val findings = UncheckedFlagAccess().lint("""
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    @Test
    fun `reports finding for call at top level without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for call inside BehindFlag function with different flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @BehindFlag("otherFeature")
            fun otherHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for call inside AssumesFlag function with different flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("otherFeature")
            fun otherHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for constructor call without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            class NewCheckoutViewModel

            fun host() { val vm = NewCheckoutViewModel() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `no finding for constructor call inside valid if`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            class NewCheckoutViewModel

            fun host(newCheckout: Boolean) {
                if (newCheckout) { val vm = NewCheckoutViewModel() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports finding for companion object member calling BehindFlag code despite class AssumesFlag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            class CheckoutContainer {
                companion object {
                    fun create() { newCheckoutScreen() }  // companion is excluded
                }
            }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for lambda body calling BehindFlag function`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            val launcher: () -> Unit = { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for BehindFlag property access without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            val newCheckoutConfig: String = "config"

            fun host() {
                val value = newCheckoutConfig
            }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `no finding for call inside AssumesFlag object body same flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            object CheckoutFeature {
                fun show() { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports finding for callable reference to BehindFlag function without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            val ref: () -> Unit = ::newCheckoutScreen
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when call site has no guard — same compilation unit`() {
        // NOTE: Detekt 1.23.8 lintWithContext accepts a single String only.
        // True cross-file detection (declaration in module A, call in module B) cannot be
        // unit-tested here. Verify cross-file behavior manually by:
        //   1. Adding @BehindFlag to a function in :core or any other module
        //   2. Calling it without a guard in :sample or :androidApp
        //   3. Running: ./gradlew detektWithTypeResolutionCommonMain
        //      (or the target-specific variant for the call-site module)
        //   Expected: UncheckedFlagAccess warning reported for the call site.
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }
}
