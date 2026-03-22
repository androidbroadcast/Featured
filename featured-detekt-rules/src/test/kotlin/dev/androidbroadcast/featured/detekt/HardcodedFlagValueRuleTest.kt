package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals

class HardcodedFlagValueRuleTest {
    private val rule = HardcodedFlagValueRule()

    @Test
    fun `reports direct access to ConfigParam defaultValue`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                val myFlag = ConfigParam("my_flag", false)

                fun check() {
                    if (myFlag.defaultValue) {
                        println("enabled")
                    }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report access to other ConfigParam properties`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                val myFlag = ConfigParam("my_flag", false)

                fun check() {
                    println(myFlag.key)
                    println(myFlag.description)
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports defaultValue access on explicit ConfigParam type`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                fun check(param: ConfigParam<Boolean>) {
                    val v = param.defaultValue
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report defaultValue chained call result`() {
        // The rule targets direct property access on simple name references.
        // Chained calls like someFactory().defaultValue are not flagged.
        val findings =
            rule.lint(
                """
                fun someFactory() = Any()

                fun check() {
                    val v = someFactory().defaultValue
                }
                """.trimIndent(),
            )

        // someFactory() contains parens so isLikelyConfigParam returns false
        assertEquals(0, findings.size)
    }
}
