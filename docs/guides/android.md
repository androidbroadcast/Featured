# Android Integration Guide

This guide walks you through integrating Featured into an Android project from scratch — from adding Gradle dependencies to using flags in a ViewModel with Compose and Firebase Remote Config.

## 1. Add Gradle dependencies

Apply the Featured Gradle plugin and declare the artifacts you need. The BOM manages all module versions from a single place.

```kotlin title="build.gradle.kts"
plugins {
    id("dev.androidbroadcast.featured") version "<version>"
}

dependencies {
    implementation(platform("dev.androidbroadcast.featured:featured-bom:<version>"))

    // Core runtime — always required
    implementation("dev.androidbroadcast.featured:core")

    // Local persistence — pick one (or both)
    implementation("dev.androidbroadcast.featured:datastore-provider")
    implementation("dev.androidbroadcast.featured:sharedpreferences-provider")

    // Remote config
    implementation("dev.androidbroadcast.featured:firebase-provider")

    // Compose extensions
    implementation("dev.androidbroadcast.featured:featured-compose")

    // Debug UI — debug builds only
    debugImplementation("dev.androidbroadcast.featured:featured-registry")
    debugImplementation("dev.androidbroadcast.featured:featured-debug-ui")
}
```

!!! note
    The Gradle plugin ID is `dev.androidbroadcast.featured`. It generates ProGuard / R8 rules and xcconfig files automatically when you build.

## 2. Declare flags

```kotlin title="shared/src/commonMain/kotlin/com/example/FeatureFlags.kt"
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.LocalFlag

object FeatureFlags {
    @LocalFlag
    val newCheckout = ConfigParam<Boolean>(
        key = "new_checkout",
        defaultValue = false,
        description = "Enable the new checkout flow",
    )

    @LocalFlag
    val maxCartItems = ConfigParam<Int>(
        key = "max_cart_items",
        defaultValue = 10,
    )
}
```

Annotate flags with `@LocalFlag` so the Gradle plugin can scan them for code generation (ProGuard rules, xcconfig).

## 3. Initialize `ConfigValues` in `Application.onCreate`

Create a single `ConfigValues` instance and call `initialize()` before the app serves any screen. `initialize()` triggers the remote provider's activation step (for Firebase: activates fetched values).

### With DataStore (recommended)

```kotlin title="MyApplication.kt"
import androidx.datastore.preferences.preferencesDataStore
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider
import dev.androidbroadcast.featured.firebase.FirebaseConfigValueProvider

val Context.featureFlagDataStore by preferencesDataStore(name = "feature_flags")

class MyApplication : Application() {

    lateinit var configValues: ConfigValues

    override fun onCreate() {
        super.onCreate()

        val localProvider = DataStoreConfigValueProvider(featureFlagDataStore)
        val remoteProvider = FirebaseConfigValueProvider()

        configValues = ConfigValues(
            localProvider = localProvider,
            remoteProvider = remoteProvider,
        )

        // Activate previously fetched remote values and trigger a background fetch
        lifecycleScope.launch {
            configValues.initialize()
            configValues.fetch()
        }
    }
}
```

### With SharedPreferences

```kotlin
import android.content.Context
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.sharedpreferences.SharedPreferencesProviderConfig

val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)
val localProvider = SharedPreferencesProviderConfig(prefs)

val configValues = ConfigValues(localProvider = localProvider)
```

## 4. Use in ViewModel with `observe`

Expose flag state as `StateFlow` so Compose (or view-based UIs) can collect it:

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidbroadcast.featured.ConfigValues
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CheckoutViewModel(
    private val configValues: ConfigValues,
) : ViewModel() {

    // StateFlow of the raw Boolean — reacts to both local and remote changes
    val isNewCheckoutEnabled: StateFlow<Boolean> =
        configValues.observe(FeatureFlags.newCheckout)
            .map { it.value }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FeatureFlags.newCheckout.defaultValue,
            )
}
```

Or use the built-in `asStateFlow` extension:

```kotlin
val isNewCheckoutEnabled: StateFlow<Boolean> = configValues.asStateFlow(
    param = FeatureFlags.newCheckout,
    scope = viewModelScope,
)
```

## 5. Compose integration

Add the `featured-compose` artifact (already listed in step 1).

### Collecting flag state in a Composable

```kotlin
@Composable
fun CheckoutScreen(configValues: ConfigValues) {
    val isEnabled: State<Boolean> = configValues.collectAsState(FeatureFlags.newCheckout)

    if (isEnabled.value) {
        NewCheckoutContent()
    } else {
        LegacyCheckoutContent()
    }
}
```

### Providing `ConfigValues` via CompositionLocal

```kotlin
// In your root composable
CompositionLocalProvider(LocalConfigValues provides configValues) {
    AppContent()
}

// Anywhere below
@Composable
fun SomeDeepComponent() {
    val configValues = LocalConfigValues.current
    val enabled by configValues.collectAsState(FeatureFlags.newCheckout)
    // …
}
```

## 6. Add Firebase Remote Config provider

`FirebaseConfigValueProvider` wraps the Firebase SDK. Add the dependency (step 1) and pass it as `remoteProvider`:

```kotlin
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dev.androidbroadcast.featured.firebase.FirebaseConfigValueProvider

// Use the default singleton (recommended)
val remoteProvider = FirebaseConfigValueProvider()

// Or supply a custom instance with non-default fetch interval
val remoteConfig = FirebaseRemoteConfig.getInstance().also { config ->
    config.setConfigSettingsAsync(
        com.google.firebase.remoteconfig.remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
    )
}
val remoteProvider = FirebaseConfigValueProvider(remoteConfig)

val configValues = ConfigValues(
    localProvider = localProvider,
    remoteProvider = remoteProvider,
)
```

After `configValues.initialize()`, remote values fetched during the previous session become active. Call `configValues.fetch()` to trigger a fresh fetch and activate the result.

## 7. Debug UI — flag override screen

`featured-debug-ui` ships a Compose screen that lists every registered flag with its current value and source, and lets you toggle or override values at runtime.

Add debug artifacts only to debug builds (step 1 already shows `debugImplementation`).

### Register flags

```kotlin
import dev.androidbroadcast.featured.registry.FlagRegistry

// Call once in Application.onCreate (or your DI module) — debug builds only
if (BuildConfig.DEBUG) {
    FlagRegistry.register(FeatureFlags.newCheckout)
    FlagRegistry.register(FeatureFlags.maxCartItems)
}
```

### Show the debug screen

```kotlin
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen

@Composable
fun DebugMenuScreen(configValues: ConfigValues) {
    FeatureFlagsDebugScreen(configValues = configValues)
}
```

Navigate to this screen from your in-app debug menu (a drawer, a shake gesture, or a long-press on the app icon shortcut).

## 8. ProGuard / R8 setup

The Gradle plugin generates `-assumevalues` rules for every `@LocalFlag`-annotated `ConfigParam<Boolean>` with `defaultValue = false`. These rules instruct R8 to treat the flag as a compile-time constant `false`, removing all guarded code from release APKs.

The task runs automatically when you build a release variant. To run it manually:

```bash
./gradlew :app:generateProguardRules
```

Output: `app/build/featured/proguard-featured.pro`

No extra configuration is needed — the plugin wires the output into the R8 pipeline automatically.

## Overriding and resetting at runtime

```kotlin
// Write a local override — survives remote fetches
configValues.override(FeatureFlags.newCheckout, true)

// Revert to the provider's stored or default value
configValues.resetOverride(FeatureFlags.newCheckout)

// Clear all local overrides
configValues.clearOverrides()
```

## Reading flags

### One-shot read

```kotlin
val configValue: ConfigValue<Boolean> = configValues.getValue(FeatureFlags.newCheckout)
if (configValue.value) {
    // feature is active
}
val source = configValue.source // DEFAULT, LOCAL, or REMOTE
```

### Reactive observation (Flow)

```kotlin
// Emits immediately with the current value, then on every change
configValues.observe(FeatureFlags.newCheckout)
    .collect { configValue ->
        println("new_checkout = ${configValue.value} (source: ${configValue.source})")
    }

// Convenience: emit only the raw value, not the ConfigValue wrapper
configValues.observe(FeatureFlags.newCheckout)
    .map { it.value }
    .collect { isEnabled: Boolean -> /* … */ }
```

## Next steps

- [iOS guide](ios.md) — Swift interop and dead-code elimination
- [JVM guide](jvm.md) — server and desktop integration
- [Providers](providers.md) — all built-in providers in detail
- [Best practices](best-practices.md) — multi-module setup and testing
