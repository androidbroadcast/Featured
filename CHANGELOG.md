# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `NSUserDefaultsConfigValueProvider` for iOS/macOS local storage (#104)
- `clear()` method on `LocalConfigValueProvider` interface (#101)
- Graceful error handling when a provider fails (#100)
- Multivariate flag support — `enum` and sealed class `ConfigParam` types (#99)
- Explicit initialization mechanism for `ConfigValues` (#98)
- `featured-testing` module with `FakeConfigValues` and test DSL (#97)
- MkDocs Material documentation website (#96)
- Bill of Materials (`featured-bom`) module (#82)
- Maven Central publishing for all modules (#81)
- `featured-debug-ui` module — Compose Multiplatform flag override UI (#79, #648f38e)
- `featured-gradle-plugin` module — code generation for Kotlin, iOS, and ProGuard (#72, #76, #80, #83, #86)
- `featured-registry` module — declarative flag scanning across modules (#74)
- `featured-compose` module — `LocalConfigValues` CompositionLocal and `collectAsState` (#73, #78)
- XCFramework published as Swift Package Manager artifact (#91)
- Combine `Publisher` support in `FeatureFlags.swift` (#88)
- Dokka API reference generation (#92)
- `FirebaseConfigValueProvider` — remote config via Firebase Remote Config
- `DataStoreConfigValueProvider` — local storage via Jetpack DataStore
- `SharedPreferencesConfigValueProvider` — local storage via SharedPreferences
- `InMemoryConfigValueProvider` — built-in in-memory local provider
- Core KMP library: `ConfigParam`, `ConfigValue`, `ConfigValues` with reactive `Flow` API
- SKIE 0.10.5 bridge for Swift interop (coroutines, sealed classes, default arguments)
- Binary Compatibility Validator (BCV) API dump for all modules (#95)
- DI pattern documentation for multi-module `ConfigValues` usage (#93)

### Fixed

- `ConfigValues.observe()` not reacting to remote provider changes (#c0e0557)
- Xcode build: JAVA_HOME, FRAMEWORK_SEARCH_PATHS, and Swift module import (#1c488ca)
- `@MainActor` and `Sendable` conformance in `FeatureFlags.swift` (#85)

[Unreleased]: https://github.com/androidbroadcast/Featured/compare/HEAD...HEAD
