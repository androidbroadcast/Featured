# Getting Started

This page gets you from zero to a working feature flag in about 5 minutes.

## Installation

### Gradle version catalog

Add the BOM to manage all module versions from a single place, then declare only the artifacts you need.

```kotlin title="settings.gradle.kts"
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

```kotlin title="build.gradle.kts"
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

!!! note
    The Gradle plugin ID is `dev.androidbroadcast.featured`. It is also published to Maven Central under the artifact `dev.androidbroadcast.featured:featured-gradle-plugin`.

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

## Step 1 — Declare a flag

Flags are plain `ConfigParam` properties. Annotate them with `@LocalFlag` so the Gradle plugin can scan them for code generation.

```kotlin title="shared/src/commonMain/kotlin/com/example/FeatureFlags.kt"
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

## Step 2 — Create a `ConfigValues` instance

Wire up providers once, typically in your dependency injection setup or `Application.onCreate`.

```kotlin title="Android"
val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(preferencesDataStore),
    remoteProvider = FirebaseConfigValueProvider(),
)
```

`ConfigValues` requires at least one provider. Both `localProvider` and `remoteProvider` are optional individually, but at least one must be non-null.

## Step 3 — Read a flag value

```kotlin
// Suspend function — call from a coroutine
val value: ConfigValue<Boolean> = configValues.getValue(FeatureFlags.newCheckout)
val isEnabled: Boolean = value.value          // the actual value
val source: ConfigValue.Source = value.source // DEFAULT, LOCAL, or REMOTE
```

## Next steps

- [Android guide](guides/android.md) — DataStore, Compose integration, and the debug UI
- [iOS guide](guides/ios.md) — Swift interop and dead-code elimination
- [Providers](guides/providers.md) — all built-in providers in detail
- [Best practices](guides/best-practices.md) — multi-module setup and testing
