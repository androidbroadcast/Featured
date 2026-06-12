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
    fun `toCamelCase two words ALL_CAPS`() {
        assertEquals("darkMode", "DARK_MODE".toCamelCase())
    }

    @Test
    fun `toCamelCase three words ALL_CAPS`() {
        assertEquals("newCheckoutFlow", "NEW_CHECKOUT_FLOW".toCamelCase())
    }

    @Test
    fun `toCamelCase single word ALL_CAPS`() {
        assertEquals("debug", "DEBUG".toCamelCase())
    }

    @Test
    fun `toCamelCase no underscores lowercases entire string`() {
        assertEquals("darkmode", "darkMode".toCamelCase())
    }

    @Test
    fun `toCamelCase mixed case with underscore`() {
        assertEquals("darkMode", "dark_mode".toCamelCase())
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

    // ── modulePathToFileSuffix ────────────────────────────────────────────────

    @Test
    fun `modulePathToFileSuffix for root app module`() {
        assertEquals("App", ":app".modulePathToFileSuffix())
    }

    @Test
    fun `modulePathToFileSuffix for nested module`() {
        assertEquals("FeatureCheckout", ":feature:checkout".modulePathToFileSuffix())
    }

    @Test
    fun `modulePathToFileSuffix for hyphenated segment`() {
        assertEquals("SampleFeatureCheckout", ":sample:feature-checkout".modulePathToFileSuffix())
    }

    @Test
    fun `modulePathToFileSuffix for deeply nested module`() {
        assertEquals("FeaturePaymentUi", ":feature:payment:ui".modulePathToFileSuffix())
    }

    @Test
    fun `modulePathToFileSuffix empty string returns Root`() {
        assertEquals("Root", "".modulePathToFileSuffix())
    }

    @Test
    fun `modulePathToFileSuffix bare colon returns Root`() {
        assertEquals("Root", ":".modulePathToFileSuffix())
    }
}
