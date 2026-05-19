# featured-debug-ui

Ready-made Compose screen for runtime flag inspection and override. Intended for debug builds only.

## Setup

```kotlin
debugImplementation("dev.androidbroadcast.featured:featured-debug-ui")
```

## Usage

Embed `FeatureFlagsDebugScreen` behind a dev-menu, shake gesture, or debug settings screen. Pass an explicit `List<ConfigParam<*>>` as the registry.

**Recommended — use the aggregator plugin** (multi-module projects): apply `dev.androidbroadcast.featured.application` in the app module and declare feature modules via `featuredAggregation(project(...))`. The plugin generates `GeneratedFeaturedRegistry.all` at build time.

```kotlin
FeatureFlagsDebugScreen(
    configValues = configValues,
    registry = GeneratedFeaturedRegistry.all,
)
```

**Alternative — inline list** (small / single-module projects):

```kotlin
FeatureFlagsDebugScreen(
    configValues = configValues,
    registry = listOf(MyFlags.flagA, MyFlags.flagB),
)
```

The screen displays all registry flags grouped by category, shows the current value and its source (DEFAULT / LOCAL / REMOTE), and allows overriding any flag value in-process. Overrides are applied via `ConfigValues.override()` and survive until the app process is restarted (or `clearOverrides()` is called).
