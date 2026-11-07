package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildTypeDefaultDslTest {
    @Test
    fun `debug override leaves release and unspecified build types on the base`() {
        val flag =
            localFlag {
                boolean("debug_only", default = false) {
                    defaultFor("debug", true)
                }
            }
        assertEquals("true", flag.effectiveDefault("debug"))
        assertEquals("false", flag.effectiveDefault("release"))
        assertEquals("false", flag.effectiveDefault("benchmark"))
    }

    @Test
    fun `debug and release overrides can differ`() {
        val flag =
            localFlag {
                boolean("release_off", default = true) {
                    defaultFor("debug", true)
                    defaultFor("release", false)
                }
            }
        assertEquals("true", flag.effectiveDefault("debug"))
        assertEquals("false", flag.effectiveDefault("release"))
        assertTrue(flag.effectiveDefault("debug") != flag.effectiveDefault("release"))
    }

    @Test
    fun `override equal to the base is stored and does not change the effective default`() {
        val flag =
            localFlag {
                boolean("same", default = false) {
                    defaultFor("debug", false)
                }
            }
        assertEquals("false", flag.buildTypeDefaults["debug"])
        assertEquals("false", flag.effectiveDefault("debug"))
        assertEquals("false", flag.effectiveDefault("release"))
    }

    @Test
    fun `serialization matches the base default form`() {
        val container =
            FlagContainer().apply {
                string("greeting", default = "hello") { defaultFor("debug", "hi") }
                enum("checkout_variant", typeFqn = "com.example.CheckoutVariant", default = "LEGACY") {
                    defaultFor("release", " OTHER ")
                }
                long("timeout", default = 5L) { defaultFor("debug", 9L) }
                float("ratio", default = 0.5f) { defaultFor("debug", 1.5f) }
            }
        val byKey = container.flags.associateBy { it.key }
        assertEquals("\"hi\"", byKey.getValue("greeting").buildTypeDefaults["debug"])
        assertEquals("OTHER", byKey.getValue("checkout_variant").buildTypeDefaults["release"])
        assertEquals("9", byKey.getValue("timeout").buildTypeDefaults["debug"])
        assertEquals("1.5", byKey.getValue("ratio").buildTypeDefaults["debug"])
    }

    @Test
    fun `remote defaultFor fails and names the flag`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                FeaturedExtension().apply {
                    remoteFlags {
                        boolean("promo_banner", default = false) {
                            defaultFor("debug", true)
                        }
                    }
                }
            }
        assertContains(error.message!!, "promo_banner")
    }

    @Test
    fun `blank build type fails and names the flag`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                localFlag { boolean("flag", default = false) { defaultFor("   ", true) } }
            }
        assertContains(error.message!!, "flag")
    }

    @Test
    fun `duplicate trimmed build type fails and names the flag`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                localFlag {
                    boolean("flag", default = false) {
                        defaultFor("debug", true)
                        defaultFor(" debug ", false)
                    }
                }
            }
        assertContains(error.message!!, "flag")
    }

    @Test
    fun `boolean flag rejects an int override and names the flag`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                localFlag { boolean("flag", default = false) { defaultFor("debug", 1) } }
            }
        assertContains(error.message!!, "flag")
    }

    @Test
    fun `string flag rejects a boolean override and names the flag`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                localFlag { string("flag", default = "hello") { defaultFor("debug", true) } }
            }
        assertContains(error.message!!, "flag")
    }

    @Test
    fun `enum flag rejects a blank override and names the flag`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                localFlag {
                    enum("flag", typeFqn = "com.example.CheckoutVariant", default = "LEGACY") {
                        defaultFor("debug", "  ")
                    }
                }
            }
        assertContains(error.message!!, "flag")
    }

    @Test
    fun `variant consumer matching prefers the longest variant name`() {
        val flavored = listOf("freeDebug", "paidDebug", "freeRelease", "paidRelease")
        assertEquals("freeDebug", matchingVariantName("compileFreeDebugKotlin", flavored))
        assertEquals("paidDebug", matchingVariantName("compilePaidDebugKotlin", flavored))
        assertEquals("freeRelease", matchingVariantName("extractFreeReleaseAnnotations", flavored))
        assertEquals("paidDebug", matchingVariantName("kspPaidDebugKotlin", flavored))
        assertEquals(null, matchingVariantName("generateConfigParamDebug", flavored))
        assertEquals(null, matchingVariantName("compileDebugKotlin", flavored))
        assertEquals("freeDebug", matchingVariantName("compileFreeDebugKotlin", flavored + "debug"))

        val plain = listOf("debug", "release", "benchmark")
        assertEquals("debug", matchingVariantName("compileDebugKotlin", plain))
        assertEquals("release", matchingVariantName("compileReleaseKotlin", plain))
        assertEquals("benchmark", matchingVariantName("compileBenchmarkKotlin", plain))
        assertEquals(null, matchingVariantName("generateConfigParam", plain))
    }

    private fun localFlag(configure: FlagContainer.() -> Unit): FlagSpec = FlagContainer().apply(configure).flags.single()
}
