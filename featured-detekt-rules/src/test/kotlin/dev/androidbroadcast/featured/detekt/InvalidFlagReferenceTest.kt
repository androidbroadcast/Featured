package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals

class InvalidFlagReferenceTest {
    private val rule = InvalidFlagReference()

    @Test
    fun `no finding when BehindFlag matches LocalFlag property in same file`() {
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val newCheckout = ConfigParam("new_checkout", false)

            @BehindFlag("newCheckout")
            fun NewCheckoutScreen() {}
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag matches RemoteFlag property in same file`() {
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.RemoteFlag

            @RemoteFlag
            val remoteFeature = ConfigParam("remote_feature", false)

            @BehindFlag("remoteFeature")
            fun RemoteFeatureScreen() {}
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports finding when BehindFlag has typo in flag name`() {
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val newCheckout = ConfigParam("new_checkout", false)

            @BehindFlag("newChekout")
            fun NewCheckoutScreen() {}
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when AssumesFlag references unknown flag on function`() {
        // @LocalFlag must be present so knownFlags is non-empty; "unknown" is not in it
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.AssumesFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val realFlag = ConfigParam("real_flag", false)

            @AssumesFlag("unknown")
            fun SomeContainer() {}
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when AssumesFlag references unknown flag on class`() {
        // @LocalFlag must be present so knownFlags is non-empty; "unknown" is not in it
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.AssumesFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val realFlag = ConfigParam("real_flag", false)

            @AssumesFlag("unknown")
            class SomeViewModel {}
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `no finding when flag registry is in a different file`() {
        // No @LocalFlag or @RemoteFlag in this snippet — rule must not false-positive
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun NewCheckoutScreen() {}
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag annotation appears before LocalFlag declaration`() {
        // Two-pass must handle ordering correctly
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.LocalFlag

            @BehindFlag("newCheckout")
            fun NewCheckoutScreen() {}

            @LocalFlag
            val newCheckout = ConfigParam("new_checkout", false)
        """.trimIndent())
        assertEquals(0, findings.size)
    }
}
