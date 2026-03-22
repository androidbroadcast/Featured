# Contributing to Featured

Thank you for your interest in contributing! This document explains how to build, test, and submit changes.

## Building

```bash
# Build all modules
./gradlew assemble

# Build a specific module
./gradlew :core:assemble
```

## Testing

```bash
# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :core:test

# Run a single test class
./gradlew :core:test --tests "dev.androidbroadcast.featured.ConfigValuesTest"

# Code coverage (core requires ≥ 90% line coverage)
./gradlew :core:koverVerify
./gradlew :core:koverHtmlReport

# Android instrumentation tests (requires a connected device or emulator)
./gradlew :core:connectedAndroidTest
```

## Code Style

The project uses Spotless for formatting. Before pushing, verify:

```bash
./gradlew spotlessCheck
# Auto-fix formatting issues
./gradlew spotlessApply
```

All public declarations must have explicit visibility modifiers (explicit API mode is enabled).
Write all code comments in English.

## Versioning Policy

Featured follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`):

| Change type | Version component |
|---|---|
| Breaking API change (removed/renamed public symbol, changed signature) | `MAJOR` bump |
| New public API, new module, new provider | `MINOR` bump |
| Bug fix, performance improvement, documentation update | `PATCH` bump |

### What counts as a breaking change

- Removing or renaming a public class, function, property, or module
- Changing a function signature in a way that requires call-site updates
- Changing the behavior of an existing API in a way that requires migration

Binary Compatibility Validator (BCV) enforces this automatically — a CI check will fail if
a public API surface changes without an explicit `apiDump` update.

## Deprecation Policy

1. An API is marked `@Deprecated` with a `ReplaceWith` suggestion and a `DeprecationLevel.WARNING`.
2. The deprecated API is kept for **at least one minor release** before being promoted to `ERROR` level.
3. APIs at `ERROR` level are removed in the **next major release**.

Example timeline: deprecated in `1.2.0` → error in `1.3.0` → removed in `2.0.0`.

## Submitting Changes

1. Fork the repository and create a branch from `main`.
2. Make your changes in a focused, single-purpose commit or small series of commits.
3. Ensure all tests pass and `spotlessCheck` is clean.
4. Open a pull request against `main` with a clear description of what changed and why.

## Module Overview

```
featured-compose ──┐
firebase-provider ─┤
datastore-provider ┼──► core
sharedprefs-provider┤
sample ─────────────┘
```

See [README.md](README.md) for a full architecture overview.
