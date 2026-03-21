# Featured

[![CI](https://github.com/AndroidBroadcast/Featured/actions/workflows/ci.yml/badge.svg)](https://github.com/AndroidBroadcast/Featured/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.androidbroadcast.featured/core.svg?label=Maven%20Central)](https://central.sonatype.com/search?q=dev.androidbroadcast.featured)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Featured** is a type-safe, reactive feature-flag and configuration management library for Kotlin Multiplatform (Android, iOS, JVM). Declare flags in shared Kotlin code, read them at runtime from local or remote providers, and let the Gradle plugin dead-code-eliminate disabled flags from your production binaries.

## Use cases

- Ship code guarded by a flag that is off by default; enable it via Firebase Remote Config when you are ready to roll out.
- Override individual flags during development or QA without touching a remote backend.
- Eliminate dead code from Release binaries: the Gradle plugin generates R8 rules (Android/JVM) and an xcconfig file (iOS) that let the respective compilers strip disabled flag code paths at build time.

## Key types

| Type | Role |
|------|------|
| `ConfigParam<T>` | Declares a named, typed configuration key with a default value |
| `ConfigValue<T>` | Wraps a param's current value and its source (DEFAULT / LOCAL / REMOTE) |
| `ConfigValues` | Container that composes local and remote providers |
| `LocalConfigValueProvider` | Interface for writable, observable local storage |
| `RemoteConfigValueProvider` | Interface for fetch-based remote configuration |

## Quick links

- [Getting Started](getting-started.md) — installation and first flag in 5 minutes
- [Android guide](guides/android.md) — DataStore, Compose, debug UI
- [iOS guide](guides/ios.md) — SKIE interop, Swift DCE via xcconfig
- [JVM guide](guides/jvm.md) — standalone JVM usage
- [Providers](guides/providers.md) — all built-in providers explained
- [API Reference](api/index.md) — full KDoc-generated reference
