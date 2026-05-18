# featured-gradle-plugin

Gradle plugin for the [Featured](../README.md) configuration management library.

Apply it to a module and declare flags in the `featured { }` DSL block; the plugin generates
typed `ConfigParam` objects, `ConfigValues` extension functions, and R8 shrinker rules.

## Enum flags

Declare an enum-typed flag with the `enum(...)` DSL function:

```kotlin
// build.gradle.kts
featured {
    localFlags {
        enum(
            key = "checkout_variant",
            typeFqn = "com.example.CheckoutVariant",
            default = "LEGACY",
        )
    }
}
```

### Runtime converter requirement (Android / JVM)

Storage-backed local providers serialize values as strings. Before the first read or write of
an enum flag you must register an `enumConverter` on the provider, otherwise the provider
throws `IllegalArgumentException` synchronously.

Affected providers and the required registration call:

| Provider | Registration |
|---|---|
| `DataStoreConfigValueProvider` | `provider.registerConverter(enumConverter<MyEnum>())` |
| `JavaPreferencesConfigValueProvider` | `provider.registerConverter(enumConverter<MyEnum>())` |
| `SharedPreferencesProviderConfig` | `provider.registerConverter(enumConverter<MyEnum>())` |

`FirebaseConfigValueProvider` handles enums automatically via reflection — no registration
is needed.

```kotlin
// Runtime wiring example (DataStore)
val provider = DataStoreConfigValueProvider(dataStore).apply {
    registerConverter(enumConverter<CheckoutVariant>())
}
val configValues = ConfigValues(localProvider = provider)
```

### iOS caveat

`NSUserDefaultsConfigValueProvider` does not support enums at this time — it has no converter
API. Use a `String` flag as a workaround on iOS and convert the raw value to your enum manually
at the call site.
