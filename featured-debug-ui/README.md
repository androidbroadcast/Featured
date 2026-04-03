# featured-debug-ui

Ready-made Compose screen for runtime flag inspection and override. Intended for debug builds only.

## Setup

```kotlin
debugImplementation("dev.androidbroadcast.featured:featured-debug-ui")
debugImplementation("dev.androidbroadcast.featured:featured-registry") // required
```

## Usage

Embed `FeatureFlagsDebugScreen` behind a dev-menu, shake gesture, or debug settings screen:

```kotlin
FeatureFlagsDebugScreen(
    registry = FlagRegistry.instance,
    configValues = configValues,
)
```

The screen displays all registered flags grouped by category, shows the current value and its source (DEFAULT / LOCAL / REMOTE), and allows overriding any flag value in-process. Overrides are applied via `ConfigValues.override()` and survive until the app process is restarted (or `clearOverrides()` is called).
