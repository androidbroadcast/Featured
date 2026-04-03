# featured-registry

Runtime registry that tracks all `ConfigValue` instances across all modules in the app.

Used by `featured-debug-ui` to enumerate every flag without manual registration.

## How it works

The `featured-gradle-plugin` task `generateFlagRegistrar` generates a `FlagRegistrar` class per module at build time. Each registrar registers its module's params into `FlagRegistry` at app startup.

## Usage

Add as `debugImplementation` — not needed in release builds.

```kotlin
debugImplementation("dev.androidbroadcast.featured:featured-registry")
```

Pair with `featured-debug-ui`. No manual setup required beyond applying the Gradle plugin.
