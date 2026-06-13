# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `ConfigValues.warmUp(params)` — new suspend function that resolves the given params in parallel
  through the full priority chain and batch-writes the sync snapshot atomically, so
  `getValueCached` returns fresh values at startup without requiring active subscribers. Call
  `configValues.warmUp(GeneratedFeaturedRegistry.all)` once at app startup. `fetch()` and
  `initialize()` now automatically refresh all warmed params after their provider completes,
  keeping the snapshot current on every remote refresh cycle. (#261)
- `FeatureFlagsDebugScreen` now includes a search field and filters. A collapsible search bar
  filters flags by key and category; an «Overridden only» chip narrows the list to flags with
  active local overrides. Empty-result states provide contextual messages with a «Clear filters»
  action. State (query, search expanded, filter) survives configuration changes. (#263)
- Gradle plugin: `VerifyExpiredFlagsTask` — on every build that runs flag code generation, the
  plugin now emits a build warning for each feature flag whose `expiresAt` date is in the past.
  The check always runs even when upstream tasks are restored from cache, and is wired into all
  codegen paths so it cannot be bypassed by invoking a single generation task. (#265)
- Gradle plugin: new `expiredFlagsMode` DSL property (`featured { expiredFlagsMode = ExpiredFlagsMode.ERROR }`).
  Default is `WARN` (behavior unchanged); `ERROR` fails the build with a `GradleException` listing
  every expired flag (module, key, expiry date) instead of emitting warnings. Invalid `expiresAt`
  format values are never escalated regardless of the mode. (#266)
- `FeatureFlagsDebugScreen` gains a «Reset all overrides» action in the top bar. A confirmation
  dialog shows the number of overrides to be cleared; after reset, a snackbar with an **Undo**
  action restores the previous values. Partial failures (reset or undo) are reported
  individually. (#269)
- `ValueResolutionStrategy` — a pluggable policy that selects the final value from the resolved
  local, remote, and default candidates. Pass it via the new optional `resolutionStrategy`
  parameter of `ConfigValues`; the built-in `ValueResolutionStrategy.Default` preserves the
  classic `local ?: remote ?: default` chain, so existing consumers are unaffected. Enables
  policies such as "kill-switch AND" (local and remote must both be `true`) and remote-only
  resolution. (#276)
- Gradle plugin: new `generation { }` DSL block for customizing the generated code — package
  (`packageName`), object names (`className`, replaces the default `Generated{Local,Remote}Flags<Suffix>`
  name entirely), and visibility (`visibility = FeaturedVisibility.PUBLIC/INTERNAL`, default INTERNAL,
  applied to the objects and their `ConfigValues` extensions). Settings can be declared module-wide in
  `featured { generation { } }` and overridden per section in `localFlags { }` / `remoteFlags { }`.
  ProGuard `-assumevalues` rules and iOS const-val files follow the local section's effective package,
  keeping release-build DCE intact with custom packages. (#278)
- `NSUserDefaultsConfigValueProvider` now supports `TypeConverter` — call
  `registerConverter(KClass, TypeConverter)` or the inline reified overload (API mirrors
  `DataStoreConfigValueProvider` 1:1). Non-primitive values are serialized to String; enum flags
  declared in the shared DSL now work on iOS, restoring cross-platform parity. (#271)
- `FeatureFlagsDebugScreen` gains an optional `onError: (Throwable) -> Unit` parameter that
  receives all internal diagnostic errors (provider failures, override write errors, reset/undo
  failures). The default behavior (print to stdout with full stack trace) is preserved; pass `{}`
  to silence. Mirrors the `ConfigValues.onProviderError` contract. (#273)

### Fixed

- Gradle plugin: `toCamelCase` conversion now fully lowercases each word before capitalising
  the first letter, so ALL_CAPS flag keys produce correct camelCase names.
  `DARK_MODE` → `darkMode` (was `darkMODE`), `NEW_CHECKOUT_FLOW` → `newCheckoutFlow`
  (was `newCHECKOUTFLOW`). **Generated function/property names change for any multi-word
  ALL_CAPS flag key** (e.g. `isDarkMODEEnabled` → `isDarkModeEnabled`). (#248)

### Changed

- `ConfigValues` default `onProviderError` handler changed from a silent no-op to platform
  logging: Android uses `Log.w("Featured", …)`, iOS uses `NSLog`, JVM writes to `System.err`.
  Pass `onProviderError = {}` to silence. Exceptions thrown by a custom handler are swallowed;
  `CancellationException` is rethrown instead of being reported as a provider error. (#260)
- `LocalConfigValueProvider.observe()` contract is now normative: the flow always emits
  immediately on collection (the stored value, or `ConfigValue(defaultValue, Source.DEFAULT)`
  when the key was never written), then on every change. `SharedPreferencesConfigValueProvider`,
  `JavaPrefsConfigValueProvider`, `NSUserDefaultsConfigValueProvider`, and
  `InMemoryConfigValueProvider` are updated to this contract. `ConfigValues.observe()` now uses
  a trigger model — local emissions are change signals that drive a full `getValue()` re-resolve
  through the priority chain, eliminating the DEFAULT-clobbers-REMOTE flicker. (#267)
- `NSUserDefaultsConfigValueProvider.clear()` now limits deletion to keys written by this
  provider instance (tracked in a persistent index); previously `clear()` wiped the entire
  UserDefaults suite, which was destructive for `standardUserDefaults`. Foreign keys in the same
  suite are preserved. `clear()` now also notifies active `observe()` flows, emitting the
  DEFAULT-sourced value for every previously stored key. (#270)
- `ConfigValues` now reads **both** providers before resolving a value; previously the remote
  provider was skipped when the local provider returned a value. Resolved values are unchanged
  under the default strategy, but `onProviderError` may now be invoked for remote failures that
  were previously masked by a local override. (#276)
- Gradle plugin: the generated `ConfigValues` extensions are now split into two files —
  `GeneratedLocalFlagExtensions<Suffix>.kt` and `GeneratedRemoteFlagExtensions<Suffix>.kt`
  (previously a single `GeneratedFlagExtensions<Suffix>.kt`) — because each section can now have its
  own package and visibility. The ProGuard `-assumevalues` class name changed accordingly; both the
  sources and the rules are regenerated together by the plugin, so no consumer action is required.
  (#278)
- Dependency updates raised `androidx.core` to 1.19.0, which requires Android consumers to build
  with `compileSdk 37` or higher; the library itself now compiles with `compileSdk 37`. (#282)

### Removed

- Gradle plugin: the `scanAllLocalFlags` root-project aggregation task has been removed. The
  plugin no longer accesses `rootProject` and is now compatible with Gradle Project Isolation.
  Use `./gradlew resolveFeatureFlags` (Gradle name-matched task invocation) to resolve flags
  across all modules that apply the plugin. (#186)

## [1.1.1] - 2026-06-04

### Fixed

- Generated ProGuard `-assumevalues` rules are now registered as `consumerProguardFiles` for
  `com.android.library` modules, ensuring rules are bundled into the AAR and applied by the
  consuming app's R8. Previously the rules were wired only via `variant.proguardFiles`, which
  has no effect in library modules and silently defeated dead-code elimination for flags
  declared in library modules. (#240)
- Flag descriptors are now wired to `ResolveFlagsTask` via a lazy `provider { }` instead of
  an eager `afterEvaluate` block. Changing a flag's `default` value in `build.gradle.kts` now
  correctly invalidates the build cache; previously the task could be served FROM-CACHE with
  the old default, causing stale generated output. (#241)

### Changed

- Maven Central releases are now promoted automatically when triggered by a version tag; the
  manual staging-promotion step in Sonatype Central Portal is no longer required. (#237)

## [1.1.0] - 2026-06-03

### Changed

- Lowered the minimum supported JDK / JVM bytecode target from 21 to 17. Featured artifacts now target JVM 17, widening consumer compatibility — projects on JDK 17 toolchains can now consume the library. Consumers on newer JDKs are unaffected. (#233)
- The Gradle plugin and its plugin marker artifact are now published to the Gradle Plugin Portal via a dedicated workflow; the Maven Central listing is kept free of plugin-marker artifacts. (#228, #231)

## [1.0.0] - 2026-05-30

### Removed

- `featured-registry` module — the runtime `FlagRegistry` global singleton and its `FlagRegistryDelegate` expect/actual are removed. Use `GeneratedFeaturedRegistry.all` (produced by the `dev.androidbroadcast.featured.application` plugin) or build an explicit `List<ConfigParam<*>>` instead.
- `featured-gradle-plugin` — `generateFlagRegistrar` task, `FlagRegistrarGenerator`, and `GenerateFlagRegistrarTask` are removed. Per-module `GeneratedFlagRegistrar.kt` files are no longer generated.
- Sample API — `registerSampleFlags()` is removed (was specific to the sample app's legacy wiring). The sample now uses `GeneratedFeaturedRegistry.all` (produced by the aggregator plugin) instead.

### Changed

- `FeatureFlagsDebugScreen` signature is now `(configValues: ConfigValues, registry: List<ConfigParam<*>>, modifier: Modifier = Modifier)` — accepts an explicit registry list instead of reading the (removed) `FlagRegistry` singleton. Pass `GeneratedFeaturedRegistry.all` for the recommended aggregator-plugin flow, or build the list inline for small projects.
- `:sample:shared` is now a pure aggregator: it applies `dev.androidbroadcast.featured.application`, declares `featuredAggregation(project(":sample:feature-*"))`, and consumes `GeneratedFeaturedRegistry.all`. The hand-written `SampleFeatureFlags.kt` is removed.
- Generator file names include a module-derived suffix (`GeneratedLocalFlagsSampleFeatureCheckout.kt`, etc.) — eliminates JVM class-name collisions when multiple modules share the same classpath. `@file:JvmName` is no longer emitted.
- `ExtensionFunctionGenerator` emits non-suspend `is*Enabled()` / `get*()` extension functions — they delegate to `getValueCached` and can be called from any context without a coroutine. Callers that previously wrapped them in `runBlocking { … }` or a coroutine scope can drop the wrapper.
- `ConfigValues.resetOverride` re-resolves the effective value synchronously through the full provider priority chain; [getValueCached] reflects the updated value immediately after the call returns.
- Generated `GeneratedLocalFlagsX` / `GeneratedRemoteFlagsX` objects are now `internal` to their declaring Gradle module — each feature module's flag declarations are an implementation detail and no longer leak across module boundaries. Cross-module flag introspection (e.g. the debug screen) flows exclusively through `GeneratedFeaturedRegistry.all`, which the aggregator plugin builds from per-module manifests. The sample app demonstrates the per-module wiring pattern: one `ConfigValues` per feature module plus a dedicated debug aggregator, all sharing the same `LocalConfigValueProvider`.
- The plugin's ProGuard-rules generation task is renamed from `generateProguardRules` to `generateFeaturedProguardRules` to avoid name collisions with other plugins. (#190)
- User documentation moved from the in-repo MkDocs site to the [GitHub Wiki](https://github.com/AndroidBroadcast/Featured/wiki); the `docs/` site and `mkdocs.yml` are removed from the repository. (#193)

### Added

- `ConfigValues.getValueCached(param: ConfigParam<T>): ConfigValue<T>` — non-suspend synchronous reader. Returns the last-written `ConfigValue<T>` from the in-memory cache; the cache is warmed on the first `getValue` / `override` / `fetch` call, and returns `Source.DEFAULT` until then.
- `ConfigValues.isEnabled(param: ConfigParam<Boolean>): Boolean` — non-suspend extension (replaces the former `suspend` variant). Delegates to `getValueCached`; safe to call from Composable functions, `init` blocks, and non-coroutine contexts.

- Featured library plugin now publishes a per-module feature-flag manifest as a consumable Gradle artifact (`featuredManifest` configuration, schema v1). Existing flag-generation pipeline is unchanged. Consumer-side aggregation arrives in a follow-up release.
- New `dev.androidbroadcast.featured.application` Gradle plugin: aggregates `featured-manifest.json` artifacts from project dependencies declared via `featuredAggregation(project(...))` and generates `object GeneratedFeaturedRegistry { val all: List<ConfigParam<*>> }` in `build/generated/featured/commonMain/`. Apply alongside `dev.androidbroadcast.featured` in the application module; wire the output directory into your source set manually (e.g., `kotlin.sourceSets.commonMain.kotlin.srcDir(...)`). Modules declaring `enum` flags also require a regular `implementation(project(...))` dependency in the consumer so the enum class is on the compile classpath; primitive-only modules need only `featuredAggregation(...)`.
- Three KMP sample feature modules — `:sample:feature-checkout`, `:sample:feature-promotions`, `:sample:feature-ui` — each declaring its own flags via the `featured { ... }` DSL. Serves as the canonical multi-module reference.
- `EnumDropdown` component in `featured-debug-ui` for overriding `enum`-typed flags in `FeatureFlagsDebugScreen`; `ConfigParam<E>` now carries `enumConstants: List<E>?` populated by codegen so the debug UI can render the dropdown without reflection.
- `featured-gradle-plugin` lives at the repo root as an included build; `pluginManagement { includeBuild("featured-gradle-plugin") }` in the root `settings.gradle.kts` exposes it to all main-build subprojects without a version coordinate.

### Fixed

- `ConfigValues.observe()` now wraps provider `Flow` collection in `catch` — exceptions thrown by a local or remote provider are routed to `onProviderError` instead of propagating and breaking the observation flow. (#196)
- Restored R8 per-function DCE: ProGuard `-assumevalues` rules now target the actual Kotlin-compiled class name (`GeneratedFlagExtensionsXKt`). The rules were silently no-op since `@file:JvmName` was removed in an earlier PR; unused boolean flags are once again eliminated at shrinking time.
- iOS framework can now `export(project(":sample:feature-*"))` without the K/N `ObjCExportCodeGenerator` crashing — requires `api(project(...))` linkage in the aggregator module so K/N has access to type adapters for generic `ConfigParam<E>` specializations.

### Platform stability

- **Android — Stable.** Public API and behavior are covered by SemVer.
- **iOS (SKIE / Swift DCE) — Preview.** Functional, but the Swift-facing API and the SPM packaging may change in minor releases without a major bump.
- **JVM — Preview.** Functional, but the API may change in minor releases without a major bump.

## [1.0.0-Beta1] - 2026-05-17

### Added

#### Core library

- Core KMP library: `ConfigParam`, `ConfigValue`, `ConfigValues` with reactive `Flow` API
- Explicit initialization mechanism for `ConfigValues` (#98)
- `clear()` method on `LocalConfigValueProvider` interface (#101)
- Graceful error handling when a provider fails (#100)
- Multivariate flag support — `enum` and sealed class `ConfigParam` types (#99)
- SKIE 0.10.10 bridge for Swift interop (coroutines, sealed classes, default arguments)
- Combine `Publisher` support in `FeatureFlags.swift` (#88)
- XCFramework published as Swift Package Manager artifact (#91)

#### Providers

- `InMemoryConfigValueProvider` — built-in in-memory local provider
- `SharedPreferencesConfigValueProvider` — local storage via SharedPreferences
- `DataStoreConfigValueProvider` — local storage via Jetpack DataStore
- `JavaPreferencesConfigValueProvider` — default JVM local provider (#167, #178)
- `NSUserDefaultsConfigValueProvider` for iOS/macOS local storage (#104)
- `FirebaseConfigValueProvider` — remote config via Firebase Remote Config

#### Gradle plugin and code generation

- `featured-gradle-plugin` module — code generation for Kotlin, iOS, and ProGuard (#72, #76, #80, #83, #86)
- Declare flags via Gradle DSL; auto-generate typed extensions and per-function R8 rules
- Enum-typed flags in Gradle DSL (#162)
- Auto-generated `FlagRegistry` initializers per module (#110)
- Auto-wired ProGuard rules into Android builds via AGP Variant API
- Configuration Cache support (Gradle 9+, AGP 9+) (#164)
- E2E integration test for `featured-gradle-plugin`
- `featured-shrinker-tests` — R8 dead-code-elimination verification module (#165)

#### Static analysis

- `featured-lint-rules` Android Lint module with `HardcodedFlagValue`, `UncheckedFlagAccess`, `ExpiredFeatureFlag`, and `InvalidFlagReference` detectors (#141, #176, #181)
- Detekt rules: `@BehindFlag` / `@AssumesFlag` annotations and `InvalidFlagReference` / `UncheckedFlagAccess` rules (#142)

#### Compose and tooling

- `featured-compose` module — `LocalConfigValues` CompositionLocal and `collectAsState` (#73, #78)
- `featured-debug-ui` module — Compose Multiplatform flag override UI (#79)
- `featured-registry` module — declarative flag scanning across modules (#74)
- `featured-testing` module with `FakeConfigValues` and test DSL (#97)

#### Packaging and docs

- Bill of Materials (`featured-bom`) module (#82)
- Maven Central publishing for all modules (#81)
- Dokka API reference generation (#92)
- MkDocs Material documentation website (#96)
- DI pattern documentation for multi-module `ConfigValues` usage (#93)
- `SECURITY.md` with vulnerability disclosure policy (#173)
- GitHub issue templates and pull-request template (#175)

### Changed

- Migrated to AGP 9.1.0 + Gradle 9.4.1 with full KMP plugin support (#135)
- Moved all provider modules under `providers/` directory (#128)

### Removed

- `binary-compatibility-validator` (BCV) plugin from all modules (#150)
- `@LocalFlag` / `@RemoteFlag` annotations from public API

### Fixed

- Swift API: `ConfigParam.description` is now exposed as `.summary` (via `@ObjCName`), avoiding the SKIE-generated `description_` workaround for the `NSObject.description()` collision
- `ConfigValues.observe()` not reacting to remote provider changes
- Xcode build: `JAVA_HOME`, `FRAMEWORK_SEARCH_PATHS`, and Swift module import
- `@MainActor` and `Sendable` conformance in `FeatureFlags.swift` (#85)
- `FirebaseConfigValueProvider.fetch()` now wraps `RuntimeException` in `FetchException` (#151)
- License mismatch: use MIT in all POM declarations (#174)
- Stale artifact IDs in quick-start docs (#179)

[Unreleased]: https://github.com/androidbroadcast/Featured/compare/v1.1.1...HEAD
[1.1.1]: https://github.com/androidbroadcast/Featured/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/androidbroadcast/Featured/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/androidbroadcast/Featured/compare/v1.0.0-Beta1...v1.0.0
[1.0.0-Beta1]: https://github.com/androidbroadcast/Featured/releases/tag/v1.0.0-Beta1
