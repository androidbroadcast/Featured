@file:JvmName("MainDesktop")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.androidbroadcast.featured.sample.checkout.CheckoutFlagsViewModel
import dev.androidbroadcast.featured.sample.promotions.PromotionsFlagsViewModel
import dev.androidbroadcast.featured.sample.ui.UiFlagsViewModel

// Each feature module gets its own ConfigValues backed by the same in-memory provider.
// Per-module ConfigValues is the pattern Featured is designed around: flags are scoped
// to the module that declared them.
fun main() {
    val sharedLocalProvider = InMemoryConfigValueProvider()

    val checkoutConfigValues = ConfigValues(localProvider = sharedLocalProvider)
    val promotionsConfigValues = ConfigValues(localProvider = sharedLocalProvider)
    val uiConfigValues = ConfigValues(localProvider = sharedLocalProvider)
    // No debug aggregator on Desktop — the Compose Desktop shell does not wire a debug-UI
    // entry. The Android shell builds a fourth `ConfigValues` for the debug screen.

    // VMs are constructed once here — the desktop application has a single-window lifetime
    // with no configuration changes, so there is no need for a ViewModelStore.
    val checkoutViewModel = CheckoutFlagsViewModel(checkoutConfigValues)
    val promotionsViewModel = PromotionsFlagsViewModel(promotionsConfigValues)
    val uiViewModel = UiFlagsViewModel(uiConfigValues)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Featured",
        ) {
            SampleApp(
                uiViewModel = uiViewModel,
                promotionsViewModel = promotionsViewModel,
                checkoutViewModel = checkoutViewModel,
            )
        }
    }
}
