package dev.androidbroadcast.featured.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExpiredFeatureFlagDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ExpiredFeatureFlagDetector()

    override fun getIssues(): List<Issue> = listOf(ExpiredFeatureFlagDetector.ISSUE)

    private val configParamStub =
        kotlin(
            """
        package dev.androidbroadcast.featured
        class ConfigParam<T : Any>(val key: String, val defaultValue: T)
        """,
        ).indented()

    private val expiresAtStub =
        kotlin(
            """
        package dev.androidbroadcast.featured
        @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
        @Retention(AnnotationRetention.SOURCE)
        annotation class ExpiresAt(val date: String)
        """,
        ).indented()

    // --- Positive tests: must report a warning ---

    @Test
    fun `reports expired flag with past date 2020-01-01`() {
        lint()
            .files(
                configParamStub,
                expiresAtStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam
                    import dev.androidbroadcast.featured.ExpiresAt

                    @ExpiresAt("2020-01-01")
                    val oldFlag = ConfigParam("old_flag", false)
                    """,
                ).indented(),
            ).run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports expired flag with past date 2021-06-15`() {
        lint()
            .files(
                configParamStub,
                expiresAtStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam
                    import dev.androidbroadcast.featured.ExpiresAt

                    @ExpiresAt("2021-06-15")
                    val anotherFlag = ConfigParam("another_flag", 42)
                    """,
                ).indented(),
            ).run()
            .expectWarningCount(1)
    }

    // --- Negative tests: must be clean ---

    @Test
    fun `does not report flag with future date`() {
        lint()
            .files(
                configParamStub,
                expiresAtStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam
                    import dev.androidbroadcast.featured.ExpiresAt

                    @ExpiresAt("2099-12-31")
                    val futureFlag = ConfigParam("future_flag", false)
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `does not report flag with malformed date`() {
        lint()
            .files(
                configParamStub,
                expiresAtStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam
                    import dev.androidbroadcast.featured.ExpiresAt

                    @ExpiresAt("invalid-date")
                    val badDateFlag = ConfigParam("bad_date_flag", false)
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `does not report ConfigParam property without ExpiresAt`() {
        lint()
            .files(
                configParamStub,
                expiresAtStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    val noAnnotation = ConfigParam("no_annotation", false)
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun `does not report past ExpiresAt on non-ConfigParam property`() {
        lint()
            .files(
                configParamStub,
                expiresAtStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ExpiresAt

                    @ExpiresAt("2020-01-01")
                    val notAFlag: String = "hello"
                    """,
                ).indented(),
            ).run()
            .expectClean()
    }
}
