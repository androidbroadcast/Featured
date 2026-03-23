package dev.androidbroadcast.featured.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HardcodedFlagValueDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = HardcodedFlagValueDetector()

    override fun getIssues(): List<Issue> = listOf(HardcodedFlagValueDetector.ISSUE)

    // Minimal stub — primary constructor of the real ConfigParam is internal,
    // so we provide a simplified version with matching val defaultValue: T.
    // T : Any matches the real non-nullable upper bound.
    private val configParamStub = kotlin(
        """
        package dev.androidbroadcast.featured
        class ConfigParam<T : Any>(val key: String, val defaultValue: T)
        """,
    ).indented()

    @Test
    fun `reports defaultValue access on ConfigParam parameter`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    fun check(param: ConfigParam<Boolean>) {
                        if (param.defaultValue) println("on")
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports defaultValue access on ConfigParam local variable`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    fun check() {
                        val flag = ConfigParam("flag", false)
                        println(flag.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports defaultValue on chained receiver`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    class Flags {
                        val darkMode = ConfigParam("dark_mode", false)
                    }

                    fun check(flags: Flags) {
                        println(flags.darkMode.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report defaultValue on String receiver`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    fun check(s: String) {
                        println(s.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `does not report access to other ConfigParam properties`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    fun check(param: ConfigParam<Boolean>) {
                        println(param.key)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `does not report correct usage via ConfigValues`() {
        // CONCERN: The detector fires on `param.defaultValue` inside ConfigValues.get's body
        // because it performs pure UAST source analysis without call-site context.
        // A future improvement would suppress warnings inside classes whose FQN matches
        // the real ConfigValues (dev.androidbroadcast.featured.ConfigValues).
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    class ConfigValues {
                        operator fun <T : Any> get(param: ConfigParam<T>): T = param.defaultValue
                    }

                    fun check(configValues: ConfigValues, flag: ConfigParam<Boolean>) {
                        val enabled = configValues[flag]
                        println(enabled)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report when no ConfigParam stub on classpath`() {
        // Sanity: without the stub, the type is unresolvable — detector stays silent.
        lint()
            .files(
                kotlin(
                    """
                    fun check(x: Any) {
                        println(x.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
