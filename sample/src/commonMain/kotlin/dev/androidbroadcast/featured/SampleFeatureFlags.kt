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
    /**
     * @LocalFlag — resolved entirely on-device via [InMemoryConfigValueProvider].
     * Drives the main button colour in [FeaturedSample].
     */
    @LocalFlag
    public val mainButtonRed: ConfigParam<Boolean> =
        ConfigParam(
            key = "main_button_red",
            defaultValue = true,
            description = "Enable red color for the main button",
            category = "ui",
        )

    /**
     * @LocalFlag — controls visibility of the "New Feature" section in [FeaturedSample].
     * When `false` the section is excluded from the composition tree entirely
     * (demonstrates the `isEnabled` guard pattern for UI entry points).
     */
    @LocalFlag
    public val newFeatureSectionEnabled: ConfigParam<Boolean> =
        ConfigParam(
            key = "new_feature_section_enabled",
            defaultValue = true,
            description = "Show the new feature section in the main screen",
            category = "ui",
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
    public val newCheckout: ConfigParam<Boolean> =
        ConfigParam(
            key = "new_checkout",
            defaultValue = false,
            description = "Enable the redesigned checkout flow",
        )

    /**
     * @RemoteFlag — resolved via a [RemoteConfigValueProvider] (e.g. Firebase Remote Config).
     * Demonstrates a multivariate (enum) flag driven by a remote backend.
     *
     * ```kotlin
     * when (configValues.getValue(checkoutVariant).value) {
     *     CheckoutVariant.LEGACY          -> LegacyCheckout()
     *     CheckoutVariant.NEW_SINGLE_PAGE -> SinglePageCheckout()
     *     CheckoutVariant.NEW_MULTI_STEP  -> MultiStepCheckout()
     * }
     * ```
     */
    @RemoteFlag
    public val checkoutVariant: ConfigParam<CheckoutVariant> =
        ConfigParam(
            key = "checkout_variant",
            defaultValue = CheckoutVariant.LEGACY,
            description = "Controls which checkout flow variant is shown to the user",
            category = "checkout",
        )

    /**
     * @RemoteFlag — controls whether a promotional banner is shown.
     * In production this would be toggled remotely without a code change.
     */
    @RemoteFlag
    public val promoBannerEnabled: ConfigParam<Boolean> =
        ConfigParam(
            key = "promo_banner_enabled",
            defaultValue = false,
            description = "Show a promotional banner on the main screen (remote-controlled)",
            category = "promotions",
        )
}
