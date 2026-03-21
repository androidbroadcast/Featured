# ADR 001: ConfigValues API for Multi-Module Usage

**Date:** 2026-03-22
**Status:** Accepted

## Context

Featured is used in Kotlin Multiplatform (KMP) apps where different feature modules each declare their own `ConfigParam` flags, but all modules share a single `ConfigValues` instance at runtime. Three design questions needed resolution:

1. Should `ConfigValues` be a singleton or injected via DI?
2. How does the debug UI (`FeatureFlagsDebugScreen`) obtain a reference to `ConfigValues`?
3. Should `FlagRegistry` be decoupled from `ConfigValues`, or should `ConfigValues` auto-register with the registry?

### Current state (before this ADR)

- `ConfigValues` is an ordinary class whose constructor accepts a `LocalConfigValueProvider` and/or `RemoteConfigValueProvider`.
- `FlagRegistry` is a separate global `object` in the `featured-registry` module; it has no connection to `ConfigValues`.
- `FeatureFlagsDebugScreen` already receives `ConfigValues` as a parameter and reads `FlagRegistry.all()` directly.
- The sample app creates `ConfigValues` inside a `@Composable` via `remember { ConfigValues(…) }`, which is convenient for the demo but is not idiomatic for production multi-module apps.

## Decision

### 1. ConfigValues is injected via DI — never a singleton

`ConfigValues` must **not** be a global singleton. It is an ordinary class that callers construct once (in their DI graph) and share across the app. This matches established Kotlin/Android DI patterns (Hilt, Koin, manual DI) and makes testing straightforward — each test constructs its own instance with a fake provider.

The no-arg `ConfigValues()` constructor visible in older BCV dumps was a Kotlin default-argument artifact. The current source already enforces `require(localProvider != null || remoteProvider != null)`, so construction without a provider is a runtime error. The API dump must stay consistent with this invariant.

### 2. FlagRegistry remains decoupled from ConfigValues

`FlagRegistry` (in the `featured-registry` module) is a separate, independent registry. `ConfigValues` does **not** reference or depend on `FlagRegistry`. Modules register their `ConfigParam` instances with `FlagRegistry.register(param)` explicitly; `ConfigValues` is unaware of the registry.

Rationale:
- Keeps the `core` module dependency-free from `featured-registry`.
- `FlagRegistry` can be used in non-UI contexts (analytics, experiment tracking) without coupling it to value resolution.
- The debug UI (`featured-debug-ui`) is the only consumer that needs both: it takes `ConfigValues` as a parameter and calls `FlagRegistry.all()` independently. This is already the existing design and it works correctly.

### 3. Debug UI receives ConfigValues via constructor/parameter

`FeatureFlagsDebugScreen` already accepts `ConfigValues` as an explicit parameter. No change is required for the debug UI. Callers pass the same `ConfigValues` instance they use everywhere else (obtained from their DI graph).

### 4. Sample app is updated to show idiomatic DI pattern

The sample app is updated so that `ConfigValues` is constructed once at the app entry point (Activity / `main`) and passed down explicitly. The `@Composable createDefaultConfigValues()` helper is removed from production code paths to avoid creating multiple instances.

## Consequences

**Positive:**
- Predictable ownership: one `ConfigValues` per app, constructed by the host.
- Testable: no hidden global state inside `ConfigValues`.
- `FlagRegistry` remains independent and composable.
- No breaking API changes to `ConfigValues`, `FlagRegistry`, or `FeatureFlagsDebugScreen`.

**Negative / trade-offs:**
- Callers must wire `ConfigValues` through their DI graph. This is minimal overhead in frameworks like Hilt or Koin.
- The sample app loses a zero-setup `remember`-based convenience, but this is appropriate — the sample's job is to demonstrate the correct pattern.

## API changes in this release

- `ConfigValues.resetOverride` was already implemented in source but absent from BCV dumps; the dump is updated to include it.
- No other public API symbols are added or removed.
