package dev.androidbroadcast.featured

import android.app.Application
import dev.androidbroadcast.featured.platform.defaultLocalProvider
import dev.androidbroadcast.featured.registry.FlagRegistry

/**
 * Application class for the sample app.
 *
 * Owns the single [ConfigValues] instance. In a real multi-module app this would be
 * provided by a DI framework (Hilt, Koin, etc.).
 */
class SampleApplication : Application() {
    val configValues: ConfigValues by lazy {
        ConfigValues(
            localProvider = defaultLocalProvider(this),
            // Uncomment to enable Firebase Remote Config:
            // remoteProvider = FirebaseConfigValueProvider(Firebase.remoteConfig),
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Register all sample flags so FeatureFlagsDebugScreen can discover them.
        // In a real multi-module app each module registers its own flags on startup.
        FlagRegistry.register(SampleFeatureFlags.mainButtonRed)
        FlagRegistry.register(SampleFeatureFlags.newFeatureSectionEnabled)
        FlagRegistry.register(SampleFeatureFlags.newCheckout)
        FlagRegistry.register(SampleFeatureFlags.checkoutVariant)
        FlagRegistry.register(SampleFeatureFlags.promoBannerEnabled)
    }
}
