package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlagDiscardDslTest {
    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    fun `discard target is recorded on a boolean false flag`() {
        val container =
            FlagContainer().apply {
                boolean("new_checkout", default = false) {
                    discard("com.example.checkout.newflow.**")
                }
            }
        assertEquals(listOf("com.example.checkout.newflow.**"), container.flags.single().discards)
    }

    @Test
    fun `multiple discard targets accumulate in order`() {
        val container =
            FlagContainer().apply {
                boolean("new_checkout", default = false) {
                    discard("com.example.a.**")
                    discard("com.example.b.Entry")
                }
            }
        assertEquals(listOf("com.example.a.**", "com.example.b.Entry"), container.flags.single().discards)
    }

    @Test
    fun `discard class spec is trimmed`() {
        val container =
            FlagContainer().apply {
                boolean("flag", default = false) { discard("  com.example.Foo  ") }
            }
        assertEquals(listOf("com.example.Foo"), container.flags.single().discards)
    }

    @Test
    fun `discardDescriptors serialises flagKey and class spec`() {
        val container =
            FlagContainer().apply {
                boolean("new_checkout", default = false) {
                    discard("com.example.a.**")
                    discard("com.example.b.Entry")
                }
                boolean("dark_mode", default = false) // no discards
            }
        assertEquals(
            listOf("new_checkout|com.example.a.**", "new_checkout|com.example.b.Entry"),
            container.discardDescriptors(),
        )
    }

    @Test
    fun `discardDescriptors is empty when no flag declares discard`() {
        val container = FlagContainer().apply { boolean("dark_mode", default = false) }
        assertTrue(container.discardDescriptors().isEmpty())
    }

    // ── validation ───────────────────────────────────────────────────────────

    @Test
    fun `discard on a boolean true flag is rejected`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                FlagContainer().apply {
                    boolean("legacy_path", default = true) { discard("com.example.Foo") }
                }
            }
        assertContains(error.message!!, "default = false")
    }

    @Test
    fun `discard on a non-boolean flag is rejected`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                FlagContainer().apply {
                    int("max_retries", default = 3) { discard("com.example.Foo") }
                }
            }
        assertContains(error.message!!, "only supported on boolean flags")
    }

    @Test
    fun `discard with a blank class spec is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            FlagContainer().apply {
                boolean("flag", default = false) { discard("   ") }
            }
        }
    }
}
