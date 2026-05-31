# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[Unreleased]: https://github.com/androidbroadcast/Featured/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/androidbroadcast/Featured/compare/v1.0.0-Beta1...v1.0.0
[1.0.0-Beta1]: https://github.com/androidbroadcast/Featured/releases/tag/v1.0.0-Beta1
