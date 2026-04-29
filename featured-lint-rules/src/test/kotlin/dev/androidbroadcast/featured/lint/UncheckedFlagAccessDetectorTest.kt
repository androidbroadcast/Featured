package dev.androidbroadcast.featured.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class UncheckedFlagAccessDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = UncheckedFlagAccessDetector()

    override fun getIssues(): List<Issue> = listOf(UncheckedFlagAccessDetector.ISSUE)

    private val behindFlagStub =
        kotlin(
            """
            package dev.androidbroadcast.featured
            annotation class BehindFlag(val flagName: String)
            """,
        ).indented()

    // ── Positive tests (must report warning) ──────────────────────────────────

    @Test
    fun `reports bare call without any guard`() {
        lint()
            .files(
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag

                    @BehindFlag("newCheckout")
                    fun NewCheckoutScreen() {}

                    fun host() {
                        NewCheckoutScreen()
                    }
                    """,
                ).indented(),
            ).run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports call inside if-block when condition does not reference the flag`() {
        lint()
            .files(
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag

                    @BehindFlag("newCheckout")
                    fun NewCheckoutScreen() {}

                    fun host(someOtherCondition: Boolean) {
                        if (someOtherCondition) {
                            NewCheckoutScreen()
                        }
                    }
                    """,
                ).indented(),
            ).run()
            .expectWarningCount(1)
    }

    // ── Negative tests (must be clean) ────────────────────────────────────────

    @Test
    fun `clean when call is inside if guarded by the flag`() {
        lint()
            .files(
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag

                    @BehindFlag("newCheckout")
                    fun NewCheckoutScreen() {}

                    fun host() {
                        val newCheckout = true
                        if (newCheckout) {
                            NewCheckoutScreen()
                        }
                    }
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `clean when containing function is annotated with same BehindFlag`() {
        lint()
            .files(
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag

                    @BehindFlag("newCheckout")
                    fun NewCheckoutScreen() {}

                    @BehindFlag("newCheckout")
                    fun CheckoutHost() {
                        NewCheckoutScreen()
                    }
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `clean when calling a function without BehindFlag annotation`() {
        lint()
            .files(
                behindFlagStub,
                kotlin(
                    """
                    fun regularFunction() {}

                    fun host() {
                        regularFunction()
                    }
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `clean when call is inside when subject guarded by the flag`() {
        lint()
            .files(
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag

                    @BehindFlag("newCheckout")
                    fun NewCheckoutScreen() {}

                    fun host() {
                        val newCheckout = true
                        when (newCheckout) {
                            true -> NewCheckoutScreen()
                            else -> {}
                        }
                    }
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }
}
