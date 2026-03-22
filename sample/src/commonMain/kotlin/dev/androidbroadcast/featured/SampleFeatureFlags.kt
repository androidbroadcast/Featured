package dev.androidbroadcast.featured

/**
 * Checkout flow variant used to demonstrate multivariate (enum) feature flags.
 */
public enum class CheckoutVariant {
    /** The original multi-screen checkout flow. */
    LEGACY,

    /** New single-page checkout (A/B experiment arm A). */
    NEW_SINGLE_PAGE,

    /** New multi-step checkout with progress indicator (A/B experiment arm B). */
    NEW_MULTI_STEP,
}

public object SampleFeatureFlags {
    public val mainButtonRed: ConfigParam<Boolean> =
        ConfigParam(
            key = "main_button_red",
            defaultValue = true,
            description = "Enable red color for the main button",
        )

    /**
     * Demonstrates multivariate (enum) feature flags.
     * Declare the param with an enum default; observe via [ConfigValues].
     *
     * ```kotlin
     * when (configValues.getValue(checkoutVariant).value) {
     *     CheckoutVariant.LEGACY          -> LegacyCheckout()
     *     CheckoutVariant.NEW_SINGLE_PAGE -> SinglePageCheckout()
     *     CheckoutVariant.NEW_MULTI_STEP  -> MultiStepCheckout()
     * }
     * ```
     */
    public val checkoutVariant: ConfigParam<CheckoutVariant> =
        ConfigParam(
            key = "checkout_variant",
            defaultValue = CheckoutVariant.LEGACY,
            description = "Controls which checkout flow variant is shown to the user",
            category = "checkout",
        )
}
