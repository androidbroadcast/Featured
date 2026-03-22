package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpiredFeatureFlagRuleTest {
    private val rule = ExpiredFeatureFlagRule()

    @Test
    fun `reports ConfigParam property with ExpiresAt date in the past`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam
                import dev.androidbroadcast.featured.ExpiresAt

                @ExpiresAt("2020-01-01")
                val oldFlag = ConfigParam("old_flag", false)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        // Message contains the property name (oldFlag), not the config key (old_flag)
        assertTrue(findings[0].message.contains("oldFlag"), "Expected property name in message: ${findings[0].message}")
        assertTrue(findings[0].message.contains("2020-01-01"), "Expected expiry date in message: ${findings[0].message}")
    }

    @Test
    fun `does not report ConfigParam property with ExpiresAt date in the future`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam
                import dev.androidbroadcast.featured.ExpiresAt

                @ExpiresAt("2099-12-31")
                val futureFlag = ConfigParam("future_flag", false)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report ConfigParam property without ExpiresAt annotation`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                val noExpiry = ConfigParam("no_expiry", false)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report non-ConfigParam property with ExpiresAt annotation`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ExpiresAt

                @ExpiresAt("2020-01-01")
                val something: String = "hello"
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports with correct message format`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam
                import dev.androidbroadcast.featured.ExpiresAt

                @ExpiresAt("2021-06-15")
                val myFlag = ConfigParam("my_flag", true)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        val message = findings[0].message
        // Message contains the property name (myFlag), not the key (my_flag)
        assertTrue(message.contains("myFlag"), "Expected property name in message: $message")
        assertTrue(message.contains("2021-06-15"), "Expected expiry date in message: $message")
    }
}
