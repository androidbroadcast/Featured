package dev.androidbroadcast.featured.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.SampleApp
import dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider
import dev.androidbroadcast.featured.datastore.registerConverter
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen
import dev.androidbroadcast.featured.enumConverter
import dev.androidbroadcast.featured.generated.GeneratedFeaturedRegistry
import dev.androidbroadcast.featured.platform.defaultLocalProvider
import dev.androidbroadcast.featured.sample.checkout.CheckoutFlagsViewModel
import dev.androidbroadcast.featured.sample.checkout.CheckoutVariant
import dev.androidbroadcast.featured.sample.promotions.PromotionsFlagsViewModel
import dev.androidbroadcast.featured.sample.ui.UiFlagsViewModel

class MainActivity : ComponentActivity() {
    // A single LocalConfigValueProvider is shared across all ConfigValues instances so
    // every module reads and writes the same underlying DataStore file. In production,
    // move this to Application scope or a DI singleton to avoid reopening the file on rotation.
    private val sharedLocalProvider by lazy {
        val provider = defaultLocalProvider(applicationContext)
        // DataStore only handles primitives natively; register a converter so that the
        // enum-typed checkoutVariant flag can be persisted and observed without throwing.
        (provider as? DataStoreConfigValueProvider)
            ?.registerConverter(enumConverter<CheckoutVariant>())
        provider
    }

    // Each feature module gets its own ConfigValues instance backed by the same provider.
    // Per-module ConfigValues is the pattern Featured is designed around: flags are scoped
    // to the module that declared them.
    private val checkoutConfigValues by lazy { ConfigValues(localProvider = sharedLocalProvider) }
    private val promotionsConfigValues by lazy { ConfigValues(localProvider = sharedLocalProvider) }
    private val uiConfigValues by lazy { ConfigValues(localProvider = sharedLocalProvider) }

    // Aggregated ConfigValues for the debug screen — observes all flags across all modules.
    private val debugConfigValues by lazy { ConfigValues(localProvider = sharedLocalProvider) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // ViewModels are created here so they are scoped to the Activity's ViewModelStore
            // and survive configuration changes. Each VM receives its module's ConfigValues.
            val checkoutViewModel = viewModel(key = "checkout") { CheckoutFlagsViewModel(checkoutConfigValues) }
            val promotionsViewModel = viewModel(key = "promotions") { PromotionsFlagsViewModel(promotionsConfigValues) }
            val uiViewModel = viewModel(key = "ui") { UiFlagsViewModel(uiConfigValues) }

            var showDebug by rememberSaveable { mutableStateOf(false) }

            if (showDebug) {
                BackHandler { showDebug = false }
                FeatureFlagsDebugScreen(configValues = debugConfigValues, registry = GeneratedFeaturedRegistry.all)
            } else {
                SampleApp(
                    uiViewModel = uiViewModel,
                    promotionsViewModel = promotionsViewModel,
                    checkoutViewModel = checkoutViewModel,
                    onOpenDebugUi = { showDebug = true },
                )
            }
        }
    }
}
