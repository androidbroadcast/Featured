package dev.androidbroadcast.featured

object SampleFeatureFlags {
    val mainButtonRed =
        ConfigParam<Boolean>(
            key = "main_button_red",
            defaultValue = true,
            description = "Enable red color for the main button",
        )

    /**
     * Demonstrates the [ExpiresAt] annotation workflow:
     *
     * 1. Flag introduced with a future expiry date.
     * 2. Once the date passes, static analysis tooling (for example, a Detekt rule
     *    such as `ExpiresAtRule`) can be configured to warn at build time.
     * 3. Team removes the flag and its associated remote config entry.
     */
    @LocalFlag
    @ExpiresAt("2026-06-01")
    val newCheckout =
        ConfigParam<Boolean>(
            key = "new_checkout",
            defaultValue = false,
            description = "Enable the redesigned checkout flow",
        )
}
