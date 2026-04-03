package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals

class InvalidFlagReferenceTest {
    private val rule = InvalidFlagReference()

    @Test
    fun `no finding when BehindFlag matches ConfigParam property in same file`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.BehindFlag

                val newCheckout = ConfigParam("new_checkout", false)

                @BehindFlag("newCheckout")
                fun NewCheckoutScreen() {}
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag matches remote ConfigParam property in same file`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.BehindFlag

                val remoteFeature = ConfigParam("remote_feature", false)

                @BehindFlag("remoteFeature")
                fun RemoteFeatureScreen() {}
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports finding when BehindFlag has typo in flag name`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.BehindFlag

                val newCheckout = ConfigParam("new_checkout", false)

                @BehindFlag("newChekout")
                fun NewCheckoutScreen() {}
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when AssumesFlag references unknown flag on function`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.AssumesFlag

                val realFlag = ConfigParam("real_flag", false)

                @AssumesFlag("unknown")
                fun SomeContainer() {}
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when AssumesFlag references unknown flag on class`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.AssumesFlag

                val realFlag = ConfigParam("real_flag", false)

                @AssumesFlag("unknown")
                class SomeViewModel {}
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    fun `no finding when flag declarations are in a different file`() {
        // No ConfigParam in this snippet — rule must not false-positive
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.BehindFlag

                @BehindFlag("newCheckout")
                fun NewCheckoutScreen() {}
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag annotation appears before ConfigParam declaration`() {
        // Two-pass ordering must be handled correctly
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.BehindFlag

                @BehindFlag("newCheckout")
                fun NewCheckoutScreen() {}

                val newCheckout = ConfigParam("new_checkout", false)
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }
}
