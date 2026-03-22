package dev.androidbroadcast.featured

import android.app.Application
import android.content.Context
import dev.androidbroadcast.featured.platform.defaultLocalProvider
import dev.androidbroadcast.featured.registry.FlagRegistry
import dev.androidbroadcast.featured.sharedpreferences.SharedPreferencesProviderConfig

/**
 * Application class for the sample app.
 *
 * Owns the single [ConfigValues] instance. In a real multi-module app this would be
 * provided by a DI framework (Hilt, Koin, etc.).
 *
 * ## Provider options demonstrated
 *
 * ### Option 1 — DataStore (default, recommended)
 * Uses [defaultLocalProvider] from `:featured-platform`, which returns a
 * [dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider] on Android.
 * Values are persisted across process restarts in a DataStore Preferences file.
 *
 * ```kotlin
 * ConfigValues(localProvider = defaultLocalProvider(context))
 * ```
 *
 * ### Option 2 — SharedPreferences
 * Use [SharedPreferencesProviderConfig] from `:sharedpreferences-provider` when
 * DataStore is not available or you need to migrate an existing SharedPreferences file.
 *
 * ```kotlin
 * val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
 * ConfigValues(localProvider = SharedPreferencesProviderConfig(prefs))
 * ```
 *
 * ### Option 3 — InMemory (non-persistent, useful for tests / debug overrides)
 * Values are discarded on process death. Useful in unit tests or when you want
 * a clean slate on every app launch.
 *
 * ```kotlin
 * ConfigValues(localProvider = InMemoryConfigValueProvider())
 * ```
 *
 * ### Option 4 — Firebase Remote Config (requires google-services.json)
 * Enabled only when the sample is built with `-PhasFirebase=true` or when
 * `sample/google-services.json` is present. See README → "Running with Firebase".
 *
 * ```kotlin
 * ConfigValues(
 *     localProvider = defaultLocalProvider(context),
 *     remoteProvider = FirebaseConfigValueProvider(),
 * )
 * ```
 *
 * ### Multi-provider composition
 * Local and remote providers can be combined freely. Remote values take precedence;
 * local overrides (written via [ConfigValues.override]) shadow both.
 */
class SampleApplication : Application() {
    val configValues: ConfigValues by lazy {
        buildConfigValues(this)
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

/**
 * Constructs the [ConfigValues] instance for the sample app.
 *
 * The local provider is [defaultLocalProvider] (DataStore-backed on Android).
 * When the sample is built with Firebase support ([BuildConfig.HAS_FIREBASE] == true),
 * a [dev.androidbroadcast.featured.firebase.FirebaseConfigValueProvider] is added as
 * the remote provider so that remote flag values override local ones.
 *
 * To switch to a SharedPreferences-backed local provider instead of DataStore, replace
 * [defaultLocalProvider] with:
 * ```kotlin
 * val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
 * SharedPreferencesProviderConfig(prefs)
 * ```
 */
private fun buildConfigValues(context: Context): ConfigValues {
    // Option 1 (active): DataStore — persistent, recommended for production.
    val localProvider = defaultLocalProvider(context)

    // Option 2 (commented out): SharedPreferences — swap in when migrating from
    // a legacy SharedPreferences store, or when DataStore is unavailable.
    //
    // val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
    // val localProvider = SharedPreferencesProviderConfig(prefs)

    return if (BuildConfig.HAS_FIREBASE) {
        // Firebase Remote Config is available — compose local + remote providers.
        // Remote values override local ones; local overrides (ConfigValues.override)
        // shadow both. The initial fetch happens lazily on first access or can be
        // triggered explicitly: lifecycleScope.launch { configValues.fetch() }
        @Suppress("UNUSED_EXPRESSION")
        "See firebase-provider module for FirebaseConfigValueProvider setup"
        // Uncomment when google-services.json is present:
        // ConfigValues(
        //     localProvider = localProvider,
        //     remoteProvider = FirebaseConfigValueProvider(),
        // )
        ConfigValues(localProvider = localProvider)
    } else {
        ConfigValues(localProvider = localProvider)
    }
}
