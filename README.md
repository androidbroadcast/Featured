# Featured

[![CI](https://github.com/AndroidBroadcast/Featured/actions/workflows/ci.yml/badge.svg)](https://github.com/AndroidBroadcast/Featured/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.androidbroadcast.featured/core.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=dev.androidbroadcast.featured)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Featured** is a type-safe, reactive feature-flag and configuration management library for Kotlin Multiplatform (Android, iOS, JVM). Declare flags in shared Kotlin code, read them at runtime from local or remote providers, and let the Gradle plugin dead-code-eliminate disabled flags from your production binaries.

## Table of contents

- [Overview](#overview)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Using flags at runtime](#using-flags-at-runtime)
- [Providers](#providers)
- [Debug UI](#debug-ui)
- [Release build optimization](#release-build-optimization)
- [iOS integration](#ios-integration)
- [Multi-module setup](#multi-module-setup)
- [API reference](#api-reference)

---

## Overview

**Use cases**

- Ship code guarded by a flag that is off by default; enable it via Firebase Remote Config when you are ready to roll out.
- Override individual flags during development or QA without touching a remote backend.
- Eliminate dead code from Release binaries: the Gradle plugin generates R8 rules (Android/JVM) and an xcconfig file (iOS) that let the respective compilers strip disabled flag code paths at build time.

**Key types**

| Type | Role |
|------|------|
| `ConfigParam<T>` | Declares a named, typed configuration key with a default value |
| `ConfigValue<T>` | Wraps a param's current value and its source (DEFAULT / LOCAL / REMOTE) |
| `ConfigValues` | Container that composes local and remote providers |
| `LocalConfigValueProvider` | Interface for writable, observable local storage |
| `RemoteConfigValueProvider` | Interface for fetch-based remote configuration |

---

## Installation

### Gradle version catalog

Add the BOM to manage all module versions from a single place, then declare only the artifacts you need.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

```kotlin
// build.gradle.kts (root or app module)
plugins {
    id("dev.androidbroadcast.featured") version "<version>"
}

dependencies {
    implementation(platform("dev.androidbroadcast.featured:featured-bom:<version>"))

    // Core runtime — always required
    implementation("dev.androidbroadcast.featured:core")

    // Optional modules — add only what you use
    implementation("dev.androidbroadcast.featured:featured-compose")         // Compose extensions
    debugImplementation("dev.androidbroadcast.featured:featured-registry")   // Flag registry for debug UI
    debugImplementation("dev.androidbroadcast.featured:featured-debug-ui")   // Debug screen

    // Local persistence providers — pick one (or both)
    implementation("dev.androidbroadcast.featured:datastore-provider")
    implementation("dev.androidbroadcast.featured:sharedpreferences-provider")

    // Remote provider
    implementation("dev.androidbroadcast.featured:firebase-provider")
}
```

> The Gradle plugin ID is `dev.androidbroadcast.featured`. It is also published to Maven Central under the artifact `dev.androidbroadcast.featured:featured-gradle-plugin`.

### iOS — Swift Package Manager

Add the package in Xcode (**File › Add Package Dependencies**) or in `Package.swift`:

```swift
.package(
    url: "https://github.com/AndroidBroadcast/Featured",
    from: "<version>"
)
```

Then add `FeaturedCore` as a target dependency:

```swift
.target(
    name: "MyApp",
    dependencies: [
        .product(name: "FeaturedCore", package: "Featured")
    ]
)
```

---

## Quick start

### 1. Declare a flag

Flags are plain `ConfigParam` properties. Annotate them with `@LocalFlag` so the Gradle plugin can scan them for code generation.

```kotlin
// shared/src/commonMain/kotlin/com/example/FeatureFlags.kt
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.LocalFlag

object FeatureFlags {
    @LocalFlag
    val newCheckout = ConfigParam<Boolean>(
        key = "new_checkout",
        defaultValue = false,
        description = "Enable the new checkout flow",
        category = "Checkout",
    )

    @LocalFlag
    val maxCartItems = ConfigParam<Int>(
        key = "max_cart_items",
        defaultValue = 10,
        description = "Maximum items allowed in cart",
    )
}
```

### 2. Create a `ConfigValues` instance

Wire up providers once, typically in your dependency injection setup or `Application.onCreate`.

```kotlin
// Android
val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(preferencesDataStore),
    remoteProvider = FirebaseConfigValueProvider(),
)
```

`ConfigValues` requires at least one provider. Both `localProvider` and `remoteProvider` are optional individually, but at least one must be non-null.

### 3. Read a flag value

```kotlin
// Suspend function — call from a coroutine
val value: ConfigValue<Boolean> = configValues.getValue(FeatureFlags.newCheckout)
val isEnabled: Boolean = value.value      // the actual value
val source: ConfigValue.Source = value.source  // DEFAULT, LOCAL, or REMOTE
```

---

## Using flags at runtime

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

### Compose extension

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

Use `LocalConfigValues` to provide a `ConfigValues` through the composition tree:

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

### iOS (Swift)

The `FeatureFlags` Swift class wraps `CoreConfigValues` (the KMP-exported type). Define your flags as `FeatureFlag` values that reference the shared `CoreConfigParam` exported from Kotlin:

```swift
import FeaturedCore

// Map a Kotlin ConfigParam to a Swift FeatureFlag
let newCheckoutFlag = FeatureFlag<Bool>(
    param: CoreFeatureFlagsCompanion().newCheckout,
    defaultValue: false
)

let featureFlags = FeatureFlags(configValues)

// Async read
let isEnabled = try await featureFlags.value(of: newCheckoutFlag)

// AsyncStream — use in a Task or async for-await loop
for await value in featureFlags.stream(of: newCheckoutFlag) {
    updateUI(value)
}

// Combine publisher
featureFlags.publisher(for: newCheckoutFlag)
    .receive(on: DispatchQueue.main)
    .sink { isEnabled in updateUI(isEnabled) }
    .store(in: &cancellables)
```

---

## Providers

### InMemoryConfigValueProvider (built-in)

No setup required. Values are stored in memory and lost on process restart. Useful for tests and previews.

```kotlin
val configValues = ConfigValues(
    localProvider = InMemoryConfigValueProvider(),
)
```

### DataStoreConfigValueProvider

Persists overrides to Jetpack DataStore Preferences.

```kotlin
// Declare once per file, outside any function or class
private val Context.featureFlagsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "feature_flags")

val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(context.featureFlagsDataStore),
)
```

### SharedPreferencesProviderConfig

Android-only. Persists overrides to SharedPreferences.

```kotlin
val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)

val configValues = ConfigValues(
    localProvider = SharedPreferencesProviderConfig(prefs),
)
```

### FirebaseConfigValueProvider (remote)

Wraps Firebase Remote Config. Remote values override local values.

```kotlin
val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(dataStore),
    remoteProvider = FirebaseConfigValueProvider(),
)

// Fetch and activate — suspend function, call from a coroutine (e.g., on app start)
lifecycleScope.launch { configValues.fetch() }
```

`FirebaseConfigValueProvider` uses `FirebaseRemoteConfig.getInstance()` by default. Pass a custom instance if you manage the Firebase lifecycle yourself:

```kotlin
FirebaseConfigValueProvider(remoteConfig = FirebaseRemoteConfig.getInstance())
```

### Override and reset at runtime

```kotlin
// Write a local override — survives remote fetches
configValues.override(FeatureFlags.newCheckout, true)

// Revert to the provider's stored or default value
configValues.resetOverride(FeatureFlags.newCheckout)
```

---

## Debug UI

`featured-debug-ui` provides a ready-made Compose screen that lists all registered flags with their current values and sources, and lets you toggle or override them at runtime.

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

Only include `featured-debug-ui` and `featured-registry` in debug builds (they are already declared that way in the installation section above):

---

## Release build optimization

### Android / JVM — R8 rules

The Gradle plugin generates ProGuard / R8 `-assumevalues` rules for every `@LocalFlag`-annotated `ConfigParam<Boolean>` with `defaultValue = false`. These rules instruct R8 to treat the flag as a constant `false` at shrink time, so all code guarded by `if (flag.value)` is removed from the release APK.

The task runs automatically when you build a release variant. To run it manually:

```bash
./gradlew :app:generateProguardRules
```

Output: `app/build/featured/proguard-featured.pro`

No extra configuration is needed — the plugin wires the output into the R8 pipeline automatically.

### iOS — xcconfig for Swift DCE

See the [iOS integration](#ios-integration) section below.

---

## iOS integration

The Gradle plugin generates an xcconfig file that feeds Swift compilation conditions into Xcode. For every `@LocalFlag`-annotated `ConfigParam<Boolean>` with `defaultValue = false`, a `DISABLE_<FLAG_KEY>` condition is generated.

### Key transformation

| Kotlin flag key | Generated condition |
|-----------------|---------------------|
| `new_checkout` | `DISABLE_NEW_CHECKOUT` |
| `experimentalUi` | `DISABLE_EXPERIMENTAL_UI` |

### Step 1 — Generate the xcconfig

```bash
./gradlew :shared:generateXcconfig
```

Output: `shared/build/featured/FeatureFlags.generated.xcconfig`

Example content:

```xcconfig
# Auto-generated by featured-gradle-plugin — do not edit
SWIFT_ACTIVE_COMPILATION_CONDITIONS = $(inherited) DISABLE_NEW_CHECKOUT DISABLE_EXPERIMENTAL_UI
```

### Step 2 — Make the file available to Xcode

Copy or symlink the file to a stable path inside your Xcode project tree:

```bash
# Copy (re-run after each generateXcconfig invocation)
cp shared/build/featured/FeatureFlags.generated.xcconfig \
   iosApp/Configuration/FeatureFlags.generated.xcconfig

# Symlink (resolved automatically)
ln -sf ../../shared/build/featured/FeatureFlags.generated.xcconfig \
   iosApp/Configuration/FeatureFlags.generated.xcconfig
```

Add the generated file to `.gitignore` if you use the copy approach:

```gitignore
iosApp/Configuration/FeatureFlags.generated.xcconfig
```

### Step 3 — Configure Xcode (one-time)

1. Open your `.xcodeproj` in Xcode.
2. Select the project in the Navigator → **Info** tab → **Configurations**.
3. Expand the **Release** configuration.
4. Set the configuration file for your app target to `FeatureFlags.generated.xcconfig`.

Only assign the xcconfig to Release. Debug builds intentionally omit it so every feature remains reachable during development.

### Step 4 — Guard Swift entry points with `#if`

```swift
// Entry point for the new checkout feature
#if !DISABLE_NEW_CHECKOUT
NewCheckoutButton()
#endif

// Deep-link handler
#if !DISABLE_NEW_CHECKOUT
case .newCheckout: NewCheckoutCoordinator.start()
#endif

// AppDelegate / SceneDelegate
#if !DISABLE_NEW_CHECKOUT
setupNewCheckoutObservers()
#endif
```

The Swift compiler removes the entire guarded block from Release binaries — zero runtime overhead.

### Automate with a pre-build Run Script phase

Add this script to your Xcode target's Build Phases (before Compile Sources). Set **Based on dependency analysis** to **off**:

```bash
cd "${SRCROOT}/.."
./gradlew :shared:generateXcconfig --quiet
cp shared/build/featured/FeatureFlags.generated.xcconfig \
   iosApp/Configuration/FeatureFlags.generated.xcconfig
```

---

## Multi-module setup

In a multi-module project, apply the Gradle plugin to every module that declares `@LocalFlag` annotations. The plugin registers a `scanLocalFlags` task per module and an aggregator task `scanAllLocalFlags` at the root.

```kotlin
// :feature:checkout module build.gradle.kts
plugins {
    id("dev.androidbroadcast.featured")
    // … other plugins
}
```

```kotlin
// :feature:profile module build.gradle.kts
plugins {
    id("dev.androidbroadcast.featured")
}
```

Run code generation tasks across all modules at once:

```bash
# Scan flags in all modules
./gradlew scanAllLocalFlags

# Generate R8 rules for all Android modules
./gradlew generateProguardRules

# Generate xcconfig across all modules
./gradlew generateXcconfig
```

Declare a single shared `ConfigValues` in your app module and inject it into feature modules through dependency injection. Feature modules declare their own `ConfigParam` objects but do not create `ConfigValues` themselves.

---

## API reference

Full KDoc-generated API reference is published to GitHub Pages:

**[https://androidbroadcast.github.io/Featured/](https://androidbroadcast.github.io/Featured/)**

Documentation is regenerated on every merge to `main`.
