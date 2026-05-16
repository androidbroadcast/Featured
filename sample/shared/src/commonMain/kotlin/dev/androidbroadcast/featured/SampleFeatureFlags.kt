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

/**
 * Feature flags for the sample app.
 *
 * In a real consumer project, Boolean/Int/String flags would be declared in
 * `build.gradle.kts` using the Featured Gradle DSL, and the plugin would generate
 * typed `ConfigParam` objects and `ConfigValues` extension functions automatically:
 *
 * ```kotlin
 * // build.gradle.kts
 * featured {
 *     localFlags {
 *         boolean("main_button_red", default = true) { category = "ui" }
 *         boolean("new_feature_section_enabled", default = true) { category = "ui" }
 *     }
 *     remoteFlags {
 *         boolean("promo_banner_enabled", default = false) {
 *             description = "Show promotional banner"
 *         }
 *     }
 * }
 * ```
 *
 * Enum-typed flags (like [checkoutVariant]) are declared manually as `ConfigParam`
 * until enum support is added to the DSL.
 *
 * The sample module is part of the library's own build and cannot apply the plugin
 * to itself, so all flags are declared manually here for demonstration purposes.
 */
public object SampleFeatureFlags {
    public val mainButtonRed: ConfigParam<Boolean> =
        ConfigParam(
            key = "main_button_red",
            defaultValue = true,
            description = "Enable red color for the main button",
            category = "ui",
        )

    public val newFeatureSectionEnabled: ConfigParam<Boolean> =
        ConfigParam(
            key = "new_feature_section_enabled",
            defaultValue = true,
            description = "Show the new feature section in the main screen",
            category = "ui",
        )

    public val newCheckout: ConfigParam<Boolean> =
        ConfigParam(
            key = "new_checkout",
            defaultValue = false,
            description = "Enable the redesigned checkout flow",
        )

    public val promoBannerEnabled: ConfigParam<Boolean> =
        ConfigParam(
            key = "promo_banner_enabled",
            defaultValue = false,
            description = "Show a promotional banner on the main screen (remote-controlled)",
            category = "promotions",
        )

    public val checkoutVariant: ConfigParam<CheckoutVariant> =
        ConfigParam(
            key = "checkout_variant",
            defaultValue = CheckoutVariant.LEGACY,
            description = "Controls which checkout flow variant is shown to the user",
            category = "checkout",
        )
}
