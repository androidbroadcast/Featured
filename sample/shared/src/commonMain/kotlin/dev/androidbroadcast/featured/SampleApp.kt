@file:Suppress("ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.androidbroadcast.featured.sample.checkout.CheckoutFlagsViewModel
import dev.androidbroadcast.featured.sample.promotions.PromotionsFlagsViewModel
import dev.androidbroadcast.featured.sample.ui.UiFlagsViewModel

/**
 * Root composable for the sample application.
 *
 * Each ViewModel corresponds to one feature module's [ConfigValues] instance —
 * demonstrating the per-module ConfigValues pattern.
 *
 * [onOpenDebugUi] is non-null in debug builds (wired by the platform shell)
 * and null in release builds, so no debug UI entry point is compiled into release.
 *
 * @param uiViewModel ViewModel for UI-related flags from :sample:feature-ui.
 * @param promotionsViewModel ViewModel for promotions flags from :sample:feature-promotions.
 * @param checkoutViewModel ViewModel for checkout flags from :sample:feature-checkout.
 * @param onOpenDebugUi Callback to navigate to the debug UI screen. Null in release builds.
 * @param modifier Optional [Modifier] for the root composable.
 */
@Composable
public fun SampleApp(
    uiViewModel: UiFlagsViewModel,
    promotionsViewModel: PromotionsFlagsViewModel,
    checkoutViewModel: CheckoutFlagsViewModel,
    onOpenDebugUi: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FeaturedSample(
        uiViewModel = uiViewModel,
        promotionsViewModel = promotionsViewModel,
        checkoutViewModel = checkoutViewModel,
        onOpenDebugUi = onOpenDebugUi,
        modifier = modifier,
    )
}
