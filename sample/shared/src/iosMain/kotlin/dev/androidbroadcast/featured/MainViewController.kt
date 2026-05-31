@file:Suppress("ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.ComposeUIViewController
import dev.androidbroadcast.featured.sample.checkout.CheckoutFlagsViewModel
import dev.androidbroadcast.featured.sample.promotions.PromotionsFlagsViewModel
import dev.androidbroadcast.featured.sample.ui.UiFlagsViewModel
import platform.UIKit.UIViewController

// Each feature module gets its own ConfigValues backed by the same in-memory provider.
// Per-module ConfigValues is the pattern Featured is designed around: flags are scoped
// to the module that declared them.
// In a real app these instances would come from a shared DI container.
public fun MainViewController(): UIViewController {
    val sharedLocalProvider = InMemoryConfigValueProvider()

    val checkoutConfigValues = ConfigValues(localProvider = sharedLocalProvider)
    val promotionsConfigValues = ConfigValues(localProvider = sharedLocalProvider)
    val uiConfigValues = ConfigValues(localProvider = sharedLocalProvider)
    // No debug aggregator on iOS — the iOS shell does not wire a debug-UI entry.
    // The Android shell builds a fourth `ConfigValues` for the debug screen.

    // VMs are constructed once per UIViewController — ConfigValues lifetimes are tied
    // to this root view controller's lifetime.
    val checkoutViewModel = CheckoutFlagsViewModel(checkoutConfigValues)
    val promotionsViewModel = PromotionsFlagsViewModel(promotionsConfigValues)
    val uiViewModel = UiFlagsViewModel(uiConfigValues)

    return ComposeUIViewController {
        SampleApp(
            uiViewModel = uiViewModel,
            promotionsViewModel = promotionsViewModel,
            checkoutViewModel = checkoutViewModel,
        )
    }
}
