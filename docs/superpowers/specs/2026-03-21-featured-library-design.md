# Featured Library — Full Design Spec

**Date:** 2026-03-21
**Status:** Approved

---

## Goal

A Kotlin Multiplatform feature flag library for multi-module apps (Android, iOS, JVM/Desktop) that supports:

1. Per-module flag declaration in Kotlin code
2. Runtime local/remote management via providers
3. Compile-time dead code elimination in release builds (Android/JVM via R8, iOS via LLVM + Swift compiler)
4. Auto-discoverable flag registry powering a debug UI panel

---

## Core Concepts (unchanged)

- **`ConfigParam<T>`** — named, typed flag with default value
- **`ConfigValue<T>`** — value + source metadata (DEFAULT, LOCAL, REMOTE, …)
- **`ConfigValues`** — runtime container, composes local + remote providers
- Providers: `InMemoryConfigValueProvider`, `DataStoreConfigValueProvider`, `SharedPreferencesConfigValueProvider`, `FirebaseConfigValueProvider`

---

## New: Annotations

Two annotations added to the `core` module:

```kotlin
/** Marks a flag as local-only. Gradle plugin will freeze its value in release builds. */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class LocalFlag

/** Marks a flag as remote-capable. Never frozen at compile time. */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class RemoteFlag
```

**Usage:**
```kotlin
// In feature module
@LocalFlag
val newCheckout = ConfigParam<Boolean>("new_checkout", defaultValue = false)

@RemoteFlag
val darkTheme = ConfigParam<Boolean>("dark_theme", defaultValue = true)
```

No annotation = local runtime, no ProGuard optimization.

---

## New: Convenience Extensions

Added to `core`:

```kotlin
suspend fun ConfigValues.isEnabled(param: ConfigParam<Boolean>): Boolean
fun ConfigValues.observeEnabled(param: ConfigParam<Boolean>): Flow<Boolean>
```

---

## New Module: `featured-gradle-plugin`

A Gradle plugin that runs during release builds and generates dead-code-elimination artifacts from `@LocalFlag`-annotated `ConfigParam` declarations.

### Inputs
- All `@LocalFlag`-annotated `ConfigParam` declarations across modules
- The `defaultValue` of each param

### Outputs per target

| Target | Generated artifact | Mechanism |
|---|---|---|
| Android / JVM | `proguard-featured.pro` | R8 `-assumevalues` |
| iOS Kotlin/Native | `FeatureFlagOverrides.kt` (`iosMain`) | `const val` → LLVM DCE |
| iOS Swift | `FeatureFlags.generated.xcconfig` | `SWIFT_ACTIVE_COMPILATION_CONDITIONS` |

### Android/JVM output example
```proguard
-assumevalues class dev.androidbroadcast.featured.InMemoryConfigValueProvider {
    # new_checkout: defaultValue=false
}
```

### iOS Kotlin/Native output example
```kotlin
// FeatureFlagOverrides.kt — auto-generated, do not edit
actual const val newCheckoutEnabled: Boolean = false
```

### iOS Swift output example
```
# FeatureFlags.generated.xcconfig — auto-generated, do not edit
SWIFT_ACTIVE_COMPILATION_CONDITIONS = $(inherited) DISABLE_NEW_CHECKOUT
```

Developer includes this xcconfig in Xcode once as a Release configuration file.

### iOS entry point pattern (developer writes once per flag)
```swift
#if !DISABLE_NEW_CHECKOUT
NewCheckoutButton()
#endif
```

---

## New Module: `featured-registry`

Central registry of all `ConfigParam` instances across modules. Powers the debug UI.

```kotlin
object FlagRegistry {
    fun register(param: ConfigParam<*>)
    fun all(): List<ConfigParam<*>>
}
```

Auto-registration: each module calls `FlagRegistry.register(...)` in an initializer, or the Gradle plugin generates a registry aggregator class.

---

## New Module: `featured-debug-ui`

A Compose Multiplatform screen showing all registered flags with override controls. **Only included in debug builds.**

- Lists all `FlagRegistry.all()` entries with current value and source
- Boolean flags: toggle switch
- String/Int/Float/Double flags: input field
- "Reset to default" per flag
- Calls `ConfigValues.override(param, value)` on change
- Excluded from release via `debugImplementation` / build variant

---

## iOS Integration Summary

Two-level approach (both required for full DCE):

1. **Kotlin/Native level** — Gradle plugin generates `const val` stubs → LLVM inlines constants → dead Kotlin branches eliminated
2. **Swift level** — Gradle plugin generates `.xcconfig` → developer wraps Swift entry points in `#if` once → Swift compiler eliminates dead branches in release

One-time Xcode setup: add `FeatureFlags.generated.xcconfig` as Release configuration.

---

## Publishing

All modules published to Maven Central:
- `dev.androidbroadcast.featured:core`
- `dev.androidbroadcast.featured:featured-gradle-plugin`
- `dev.androidbroadcast.featured:featured-registry`
- `dev.androidbroadcast.featured:featured-debug-ui`
- `dev.androidbroadcast.featured:datastore-provider`
- `dev.androidbroadcast.featured:sharedpreferences-provider`
- `dev.androidbroadcast.featured:firebase-provider`
- `dev.androidbroadcast.featured:featured-compose`

---

## Module Dependency Graph (updated)

```
featured-compose ──┐
firebase-provider ─┤
datastore-provider ┼──► core ◄── featured-registry ◄── featured-debug-ui
sharedprefs-provider┤
sample ─────────────┘         featured-gradle-plugin (buildSrc/plugin)
```
