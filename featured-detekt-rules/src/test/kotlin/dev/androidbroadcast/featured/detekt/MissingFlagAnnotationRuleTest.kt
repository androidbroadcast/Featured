package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals

class MissingFlagAnnotationRuleTest {
    private val rule = MissingFlagAnnotationRule()

    @Test
    fun `reports ConfigParam property missing both LocalFlag and RemoteFlag`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                val unannotated = ConfigParam("unannotated", false)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `does not report ConfigParam property with LocalFlag annotation`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam
                import dev.androidbroadcast.featured.LocalFlag

                @LocalFlag
                val localFlag = ConfigParam("local_flag", false)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report ConfigParam property with RemoteFlag annotation`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam
                import dev.androidbroadcast.featured.RemoteFlag

                @RemoteFlag
                val remoteFlag = ConfigParam("remote_flag", false)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report non-ConfigParam property without annotations`() {
        val findings =
            rule.lint(
                """
                val plainString = "hello"
                val plainBool = true
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports correct property name in message`() {
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                val myFeatureFlag = ConfigParam("my_feature", true)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        val message = findings[0].message
        assert(message.contains("myFeatureFlag")) { "Expected property name in message: $message" }
    }

    @Test
    fun `does not report ConfigParam inside function`() {
        // Local variables inside functions are not top-level or member properties,
        // they don't need flag annotations
        val findings =
            rule.lint(
                """
                import dev.androidbroadcast.featured.ConfigParam

                fun createParam(): ConfigParam<Boolean> {
                    val localParam = ConfigParam("local", false)
                    return localParam
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }
}
