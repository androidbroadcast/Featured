package dev.androidbroadcast.featured.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class InvalidFlagReferenceDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = InvalidFlagReferenceDetector()

    override fun getIssues(): List<Issue> = listOf(InvalidFlagReferenceDetector.ISSUE)

    private val configParamStub =
        kotlin(
            """
            package dev.androidbroadcast.featured
            class ConfigParam<T : Any>(val key: String, val defaultValue: T)
            """,
        ).indented()

    private val behindFlagStub =
        kotlin(
            """
            package dev.androidbroadcast.featured
            annotation class BehindFlag(val flagName: String)
            """,
        ).indented()

    private val assumesFlagStub =
        kotlin(
            """
            package dev.androidbroadcast.featured
            annotation class AssumesFlag(val flagName: String)
            """,
        ).indented()

    // ── Positive tests (must report a warning) ────────────────────────────────

    @Test
    fun `reports BehindFlag with typo when ConfigParam exists`() {
        lint()
            .files(
                configParamStub,
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag
                    import dev.androidbroadcast.featured.ConfigParam

                    val newCheckout = ConfigParam("new_checkout", false)

                    @BehindFlag("typo")
                    fun NewCheckoutScreen() {}
                    """,
                ).indented(),
            )
            // IMPORT_ALIAS and JVM_OVERLOADS test modes re-run the detector on a rewritten
            // copy of the file, producing a duplicate incident at the same location.
            // allowDuplicates() accepts this — the warning is still verified to be present.
            .allowDuplicates()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports AssumesFlag with wrong name when ConfigParam exists`() {
        lint()
            .files(
                configParamStub,
                assumesFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.AssumesFlag
                    import dev.androidbroadcast.featured.ConfigParam

                    val newCheckout = ConfigParam("new_checkout", false)

                    @AssumesFlag("wrongName")
                    fun NewCheckoutScreen() {}
                    """,
                ).indented(),
            )
            // See comment in `reports BehindFlag with typo` for why allowDuplicates() is needed.
            .allowDuplicates()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports BehindFlag with wrong case — flag names are case-sensitive`() {
        lint()
            .files(
                configParamStub,
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag
                    import dev.androidbroadcast.featured.ConfigParam

                    val newCheckout = ConfigParam("new_checkout", false)

                    @BehindFlag("NewCheckout")
                    fun NewCheckoutScreen() {}
                    """,
                ).indented(),
            )
            // See comment in `reports BehindFlag with typo` for why allowDuplicates() is needed.
            .allowDuplicates()
            .run()
            .expectWarningCount(1)
    }

    // ── Negative tests (must be clean) ────────────────────────────────────────

    @Test
    fun `clean when BehindFlag name matches ConfigParam property`() {
        lint()
            .files(
                configParamStub,
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag
                    import dev.androidbroadcast.featured.ConfigParam

                    val newCheckout = ConfigParam("new_checkout", false)

                    @BehindFlag("newCheckout")
                    fun NewCheckoutScreen() {}
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `clean when AssumesFlag name matches ConfigParam property`() {
        lint()
            .files(
                configParamStub,
                assumesFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.AssumesFlag
                    import dev.androidbroadcast.featured.ConfigParam

                    val darkMode = ConfigParam("dark_mode", false)

                    @AssumesFlag("darkMode")
                    fun DarkModeAwareScreen() {}
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `clean when file has no ConfigParam properties — skip to avoid false positives`() {
        // Files without ConfigParam declarations (e.g. generated code consuming flags from
        // another module) must not produce any warnings.
        lint()
            .files(
                configParamStub,
                behindFlagStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.BehindFlag

                    @BehindFlag("someFlag")
                    fun SomeScreen() {}
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `clean when unrelated annotation with string arg is present`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    annotation class Unrelated(val value: String)

                    val myFlag = ConfigParam("my_flag", false)

                    @Unrelated("anything")
                    fun SomeScreen() {}
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }
}
