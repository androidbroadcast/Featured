# Featured

[![CI](https://github.com/AndroidBroadcast/Featured/actions/workflows/ci.yml/badge.svg)](https://github.com/AndroidBroadcast/Featured/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.androidbroadcast.featured/featured-core.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=dev.androidbroadcast.featured)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Featured is a type-safe, reactive feature-flag and configuration management library for Kotlin Multiplatform — Android, iOS (via SKIE), and JVM.

## Highlights

- **Type-safe flags** — declared in the Gradle DSL, accessed via generated typed extensions on `ConfigValues`. No string keys, no unchecked casts.
- **Dead-code elimination in release builds** — a flag with `default = false` makes the guarded code unreachable. The Gradle plugin emits R8 `-assumevalues` rules (Android/JVM) and an xcconfig with `DISABLE_<FLAG>` Swift compilation conditions (iOS), so the respective compilers physically strip disabled branches from release binaries.
- **Reactive** — every value is observable via `Flow`; Compose and SwiftUI/Combine integrations included.
- **Multiple providers** — DataStore, SharedPreferences, NSUserDefaults, JavaPreferences, Firebase Remote Config, ConfigCat, or a custom one.
- **Debug UI** — a ready-made Compose screen for overriding flags at runtime.

## Quick example

```kotlin
// build.gradle.kts — declare the flag
plugins {
    id("dev.androidbroadcast.featured") version "<version>"
}

dependencies {
    implementation(platform("dev.androidbroadcast.featured:featured-bom:<version>"))
    implementation("dev.androidbroadcast.featured:featured-core")
    implementation("dev.androidbroadcast.featured:featured-datastore-provider")
}

featured {
    localFlags {
        boolean("new_checkout", default = false) {
            description = "Enable the new checkout flow"
        }
    }
}
```

```kotlin
// Application.kt — wire up ConfigValues once
val dataStore = PreferenceDataStoreFactory.create { context.dataStoreFile("feature_flags.preferences_pb") }

val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(dataStore),
)
```

```kotlin
// Read the generated extension anywhere
val isEnabled: Boolean = configValues.isNewCheckoutEnabled()
```

## Multi-module pattern

In a multi-module app, construct one `ConfigValues` per feature module plus one debug aggregator,
all sharing the same `LocalConfigValueProvider`:

```kotlin
// Construct one ConfigValues per feature module + one debug aggregator, all over a shared provider
val sharedLocal: LocalConfigValueProvider = DataStoreConfigValueProvider(context, …)

val checkoutConfig = ConfigValues(localProvider = sharedLocal)
val promotionsConfig = ConfigValues(localProvider = sharedLocal)
val uiConfig = ConfigValues(localProvider = sharedLocal)

// Debug-only aggregator that the FeatureFlagsDebugScreen drives
val debugConfig = ConfigValues(localProvider = sharedLocal)

FeatureFlagsDebugScreen(
    configValues = debugConfig,
    registry = GeneratedFeaturedRegistry.all,
)
```

Each feature module owns its own `ConfigValues` and observes only its own flags (via public
observe-bridge extensions). The generated `GeneratedLocalFlagsX` / `GeneratedRemoteFlagsX` objects
are `internal` to their module — cross-module flag listing flows exclusively through
`GeneratedFeaturedRegistry.all`, which is built from the per-module manifests by the aggregator
plugin. The single source of truth for stored overrides is the shared `LocalConfigValueProvider`,
so writes from any instance propagate to every other one through its reactive `observe` flow.

## Documentation

Full documentation lives in the [Wiki](https://github.com/AndroidBroadcast/Featured/wiki):

- [Getting Started](https://github.com/AndroidBroadcast/Featured/wiki/Getting-Started)
- [Installation](https://github.com/AndroidBroadcast/Featured/wiki/Installation)
- [Providers](https://github.com/AndroidBroadcast/Featured/wiki/Providers)
- [Release Optimization (DCE)](https://github.com/AndroidBroadcast/Featured/wiki/Release-Optimization) — how flags get stripped from release binaries
- [iOS Usage](https://github.com/AndroidBroadcast/Featured/wiki/iOS-Usage)
- [Best Practices](https://github.com/AndroidBroadcast/Featured/wiki/Best-Practices)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).

## License

MIT — see [LICENSE](LICENSE).
