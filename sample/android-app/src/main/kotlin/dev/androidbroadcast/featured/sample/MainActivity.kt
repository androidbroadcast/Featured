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
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.SampleApp
import dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider
import dev.androidbroadcast.featured.datastore.registerConverter
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen
import dev.androidbroadcast.featured.enumConverter
import dev.androidbroadcast.featured.generated.GeneratedFeaturedRegistry
import dev.androidbroadcast.featured.platform.defaultLocalProvider
import dev.androidbroadcast.featured.sample.checkout.CheckoutVariant

class MainActivity : ComponentActivity() {
    // ConfigValues is held at Activity scope for this sample.
    // In production, move to Application or a DI singleton to avoid
    // recreating (and re-opening) the DataStore file on every rotation.
    private val configValues by lazy {
        val localProvider = defaultLocalProvider(applicationContext)
        // DataStore only handles primitives natively; register a converter so that the
        // enum-typed checkoutVariant flag can be persisted and observed without throwing.
        (localProvider as? DataStoreConfigValueProvider)
            ?.registerConverter(enumConverter<CheckoutVariant>())
        ConfigValues(localProvider = localProvider)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showDebug by rememberSaveable { mutableStateOf(false) }

            if (showDebug) {
                BackHandler { showDebug = false }
                FeatureFlagsDebugScreen(configValues = configValues, registry = GeneratedFeaturedRegistry.all)
            } else {
                SampleApp(
                    configValues = configValues,
                    onOpenDebugUi = { showDebug = true },
                )
            }
        }
    }
}
