# Sample CLAUDE.md

The sample is intentionally a multi-module demonstration of the Featured plugin family.

## Module map

- `:sample:feature-checkout` — owns `CheckoutVariant` enum + 2 local flags (`new_checkout` Boolean, `checkout_variant` enum).
- `:sample:feature-promotions` — 1 remote flag (`promo_banner_enabled` Boolean).
- `:sample:feature-ui` — 2 local UI flags (`main_button_red` Boolean, `new_feature_section_enabled` Boolean).
- `:sample:shared` — pure aggregator (`dev.androidbroadcast.featured.application`). Contains Compose UI (`FeaturedSample`, `SampleApp`) and `SampleViewModel`. No flag declarations of its own.
- `:sample:android-app` — Activity shell; wires `DataStoreConfigValueProvider` + `FeatureFlagsDebugScreen`.
- `:sample:desktop` — JVM shell; uses `InMemoryConfigValueProvider`.
- `iosApp/` — Xcode project consuming `FeaturedSampleApp.framework` (static, produced by `:sample:shared`).

## Observe-bridge convention

Each `:sample:feature-*` module ships `*FlagObservers.kt` with public `ConfigValues` extensions
(e.g. `mainButtonRedFlow()`, `setMainButtonRed()`). UI consumers should call these instead of
referencing `GeneratedLocalFlags*` / `GeneratedRemoteFlags*` directly.

For non-reactive reads (logging, eager-conditional code paths) use `configValues.getValueCached(param)`
directly — the codegen-emitted `is*Enabled()` / `get*()` extensions are non-suspend and call this
under the hood.

## Adding a flag

1. Edit the feature module's `build.gradle.kts` — add a declaration inside `featured { localFlags { ... } }` or `featured { remoteFlags { ... } }`.
2. Add a public observer / setter in `*FlagObservers.kt`.
3. If the UI needs it, expose a `StateFlow` + setter in `SampleViewModel`.

## Aggregation

`:sample:shared` declares `featuredAggregation(project(":sample:feature-*"))` for all three modules and wires the `generateFeaturedRegistry` task output into `commonMain`. The resulting `GeneratedFeaturedRegistry.all` is passed to `FeatureFlagsDebugScreen`.

## Multi-module wiring

The sample constructs one `ConfigValues` per `:sample:feature-*` module plus one debug aggregator (`debugConfigValues`). All four instances share the same `LocalConfigValueProvider` so overrides toggled in the debug screen propagate to every per-module instance via the shared DataStore's reactive `observe`. Each feature module's flag declarations are encapsulated behind its `internal` `GeneratedLocalFlagsX` object and exposed only via public observe-bridge extensions (`*FlagObservers.kt`) and a per-feature `ViewModel` that takes only its own `ConfigValues`.

This is the canonical demonstration of the recommended pattern for real apps: a 20-module app wires 20 production `ConfigValues` + 1 debug aggregator over a single DataStore.
