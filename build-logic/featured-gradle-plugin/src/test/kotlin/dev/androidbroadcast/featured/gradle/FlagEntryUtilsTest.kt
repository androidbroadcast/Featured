package dev.androidbroadcast.featured.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class FlagEntryUtilsTest {
    // ── toCamelCase ───────────────────────────────────────────────────────────

    @Test
    fun `toCamelCase single word lowercase`() {
        assertEquals("flag", "flag".toCamelCase())
    }

    @Test
    fun `toCamelCase two words`() {
        assertEquals("darkMode", "dark_mode".toCamelCase())
    }

    @Test
    fun `toCamelCase three words`() {
        assertEquals("maxRetryCount", "max_retry_count".toCamelCase())
    }

    @Test
    fun `toCamelCase all uppercase input lowercases first segment capitalises rest`() {
        assertEquals("darkMODE", "DARK_MODE".toCamelCase())
    }

    @Test
    fun `toCamelCase no underscores lowercases entire string`() {
        assertEquals("darkmode", "darkMode".toCamelCase())
    }

    // ── modulePathToIdentifier ────────────────────────────────────────────────

    @Test
    fun `modulePathToIdentifier for root app module`() {
        assertEquals("App", ":app".modulePathToIdentifier())
    }

    @Test
    fun `modulePathToIdentifier for nested module`() {
        assertEquals("FeatureCheckout", ":feature:checkout".modulePathToIdentifier())
    }

    @Test
    fun `modulePathToIdentifier for deeply nested module`() {
        assertEquals("FeaturePaymentUi", ":feature:payment:ui".modulePathToIdentifier())
    }

    @Test
    fun `modulePathToIdentifier without leading colon`() {
        assertEquals("App", "app".modulePathToIdentifier())
    }

    @Test
    fun `modulePathToIdentifier empty string returns Root`() {
        assertEquals("Root", "".modulePathToIdentifier())
    }

    @Test
    fun `modulePathToIdentifier bare colon returns Root`() {
        assertEquals("Root", ":".modulePathToIdentifier())
    }
}
