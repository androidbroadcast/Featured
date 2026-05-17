# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0-Beta1] - 2026-05-17

### Added

#### Core library

- Core KMP library: `ConfigParam`, `ConfigValue`, `ConfigValues` with reactive `Flow` API
- Explicit initialization mechanism for `ConfigValues` (#98)
- `clear()` method on `LocalConfigValueProvider` interface (#101)
- Graceful error handling when a provider fails (#100)
- Multivariate flag support — `enum` and sealed class `ConfigParam` types (#99)
- SKIE 0.10.5 bridge for Swift interop (coroutines, sealed classes, default arguments)
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

- Migrated to AGP 9.1.0 + Gradle 9.3.1 with full KMP plugin support (#135)
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

[Unreleased]: https://github.com/androidbroadcast/Featured/compare/v1.0.0-Beta1...HEAD
[1.0.0-Beta1]: https://github.com/androidbroadcast/Featured/releases/tag/v1.0.0-Beta1
