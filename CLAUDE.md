# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Featured** is a Kotlin Multiplatform (KMP) configuration management library supporting Android, iOS (via SKIE), and JVM. It provides a type-safe, reactive configuration system with swappable local and remote providers.

## Core Concepts

- **`ConfigParam<T>`** — declares a named, typed configuration key with a default value
- **`ConfigValue<T>`** — wraps a param + its current value; supports reactive observation via `Flow`
- **`ConfigValues`** — container holding all `ConfigValue` instances; accepts optional local and remote providers
- **`LocalConfigValueProvider` / `RemoteConfigValueProvider`** — interfaces implemented by each provider module

**Provider priority:** remote values override local values when both are present.

## Key Conventions

- **Explicit API mode** on all KMP modules — every public declaration requires an explicit visibility modifier
- **Version catalog** (`gradle/libs.versions.toml`) is the single source of truth for dependency versions
- **Formatting:** run `./gradlew spotlessCheck` before pushing; `./gradlew spotlessApply` to auto-fix
