# Android Guide

This guide covers Android-specific integration: reading flags reactively, using Compose extensions, and the debug UI.

## Reading flags

### One-shot read

```kotlin
val configValue: ConfigValue<Boolean> = configValues.getValue(FeatureFlags.newCheckout)
if (configValue.value) {
    // feature is active
}
```

### Reactive observation (Flow)

```kotlin
// Emits immediately with the current value, then on every change
configValues.observe(FeatureFlags.newCheckout)
    .collect { configValue ->
        println("new_checkout = ${configValue.value} (source: ${configValue.source})")
    }

// Convenience: emit only the raw value, not the ConfigValue wrapper
configValues.observeValue(FeatureFlags.newCheckout)
    .collect { isEnabled: Boolean -> /* … */ }

// Convert to StateFlow
val isEnabled: StateFlow<Boolean> = configValues.asStateFlow(
    param = FeatureFlags.newCheckout,
    scope = viewModelScope,
)
```

## Compose integration

Add the `featured-compose` artifact to your dependencies:

```kotlin
implementation("dev.androidbroadcast.featured:featured-compose")
```

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

## Debug UI

`featured-debug-ui` provides a ready-made Compose screen that lists all registered flags with their current values and sources, and lets you toggle or override them at runtime.

Add the debug artifacts only to debug builds:

```kotlin
debugImplementation("dev.androidbroadcast.featured:featured-registry")
debugImplementation("dev.androidbroadcast.featured:featured-debug-ui")
```

### 1. Register flags

Register each `ConfigParam` in the `FlagRegistry` so the debug screen can discover them:

```kotlin
import dev.androidbroadcast.featured.registry.FlagRegistry

// Call once on app start (e.g., in Application.onCreate or your DI module)
FlagRegistry.register(FeatureFlags.newCheckout)
FlagRegistry.register(FeatureFlags.maxCartItems)
```

### 2. Show the debug screen

```kotlin
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen

@Composable
fun DebugMenuScreen(configValues: ConfigValues) {
    FeatureFlagsDebugScreen(configValues = configValues)
}
```

## Release build optimization — R8 rules

The Gradle plugin generates ProGuard / R8 `-assumevalues` rules for every `@LocalFlag`-annotated `ConfigParam<Boolean>` with `defaultValue = false`. These rules instruct R8 to treat the flag as a constant `false` at shrink time, so all code guarded by `if (flag.value)` is removed from the release APK.

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
```
