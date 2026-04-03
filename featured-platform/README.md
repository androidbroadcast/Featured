# featured-platform

Platform-specific default provider resolution.

## Usage

```kotlin
implementation("dev.androidbroadcast.featured:featured-platform")
```

`DefaultLocalProvider()` returns the recommended `LocalConfigValueProvider` for the current platform:

| Platform | Provider |
|----------|---------|
| Android | DataStore |
| iOS | NSUserDefaults |
| JVM | Java Preferences |

```kotlin
val configValues = ConfigValues(
    localProvider = DefaultLocalProvider(),
    remoteProvider = firebaseProvider,
)
```

If you need custom local persistence (e.g. encrypted storage), implement `LocalConfigValueProvider` directly and skip this module.
