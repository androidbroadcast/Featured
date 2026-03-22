package dev.androidbroadcast.featured

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Verifies that [@ExpiresAt] can be applied to [ConfigParam] properties.
 *
 * Because [@ExpiresAt] has SOURCE retention there is nothing to inspect at runtime.
 * These tests exist solely to ensure the annotation compiles correctly and is usable
 * in the expected positions without affecting the runtime behaviour of [ConfigParam].
 */
class ExpiresAtTest {
    @ExpiresAt("2026-06-01")
    val flagWithExpiry = ConfigParam<Boolean>("expires_at_flag", defaultValue = false)

    @ExpiresAt("2025-01-01")
    val flagWithPastExpiry = ConfigParam<String>("past_expiry_flag", defaultValue = "off")

    @Test
    fun annotationCanBeAppliedToConfigParamProperty() {
        // If this file compiles, the annotation is valid on a ConfigParam property.
        // The flag key is accessible as normal — the annotation does not change runtime behaviour.
        assertEquals("expires_at_flag", flagWithExpiry.key)
    }

    @Test
    fun annotationDoesNotAffectConfigParamDefaultValue() {
        assertEquals(false, flagWithExpiry.defaultValue)
    }

    @Test
    fun multipleExpiriesCanCoexistOnDifferentProperties() {
        assertNotEquals(flagWithExpiry.key, flagWithPastExpiry.key)
    }
}
