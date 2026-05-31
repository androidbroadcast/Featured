# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Featured** is a Kotlin Multiplatform feature-flag / configuration management library (Android, iOS via SKIE, JVM / Compose Desktop). Two things make it more than a typed wrapper around shared-prefs:

1. **A Gradle plugin family** generates typed `ConfigParam` objects + ergonomic `ConfigValues` extensions from a `featured { localFlags { … } }` DSL — no hand-written keys.
2. **Release-time dead-code elimination.** Flags with `default = false` emit per-function R8 `-assumevalues` rules (Android/JVM) and an xcconfig with `DISABLE_<FLAG>` Swift conditions (iOS). Disabled branches are physically stripped from release binaries.

`develop` is the integration branch; PRs target `develop`, not `main`.

## Core Concepts

- **`ConfigParam<T>`** — declared name + typed default. The Gradle plugin emits these as `object GeneratedLocalFlagsX` / `GeneratedRemoteFlagsX` per-module (since PR #202: **`internal`**, not public).
- **`ConfigValue<T>`** — observable value (`Flow<T>`) for a single `ConfigParam`.
- **`ConfigValues`** — container; constructed with optional `LocalConfigValueProvider` and `RemoteConfigValueProvider`. **Remote overrides local.** Apps normally construct **one `ConfigValues` per feature module**, all sharing the same provider.
- **Aggregator plugin (`dev.androidbroadcast.featured.application`)** — consumes `featured-manifest.json` from every `featuredAggregation(project(...))` dependency and generates `GeneratedFeaturedRegistry.all: List<ConfigParam<*>>`. This is the *only* cross-module flag listing surface; the per-module generated objects stay `internal`.
- **Observe-bridge convention** — each feature module ships public `ConfigValues` extensions (`fooFlow()`, `setFoo()`) so the UI never references `GeneratedLocalFlagsX` directly.

## Module Map

```
core ───────────────── public abstractions (ConfigParam/Value/Values, provider interfaces)
featured-compose ──── Compose-Multiplatform extension (collectAsState helpers)
featured-debug-ui ─── FeatureFlagsDebugScreen (UI-agnostic, reads GeneratedFeaturedRegistry.all)
featured-testing ──── test doubles (InMemoryConfigValueProvider, etc.)
featured-platform ─── platform metadata module
featured-bom ──────── Maven BOM
featured-detekt-rules / featured-lint-rules ── static checks for flag misuse
featured-shrinker-tests ── R8 DCE integration tests
providers/{configcat,datastore,firebase,javaprefs,nsuserdefaults,sharedpreferences}
featured-gradle-plugin/ ── published Gradle plugin (included build, dogfooded on :sample:feature-*)
sample/{shared,feature-checkout,feature-promotions,feature-ui,android-app,desktop}
iosApp/   Swift consumer of FeaturedSampleApp.framework
```

The plugin is structured as an **included build** (`includeBuild("featured-gradle-plugin")` in the root `settings.gradle.kts`), not a regular subproject. This breaks the chicken-and-egg cycle of applying the plugin to `:sample:feature-*` modules within the same repo for dogfooding.

## Build / Test Commands

```bash
./gradlew assemble                          # build everything
./gradlew test                              # all JVM unit tests
./gradlew :core:test                        # one module
./gradlew :core:test --tests "dev.androidbroadcast.featured.ConfigValuesTest"
./gradlew :core:koverVerify                 # core requires >=90% line coverage
./gradlew :core:koverHtmlReport
./gradlew :core:connectedAndroidTest        # needs device/emulator
./gradlew :featured-debug-ui:allTests       # KMP module — JVM + Android + iOS targets
./gradlew :featured-gradle-plugin:test      # plugin unit tests (43+ cases)
./gradlew spotlessCheck                     # required before push
./gradlew spotlessApply                     # auto-fix
./gradlew publishToMavenLocal               # publish the Gradle plugin locally
```

Sample build / install:

```bash
./gradlew :sample:android-app:installDebug  # Android sample
./gradlew :sample:desktop:run               # Compose Desktop sample
```

**Plugin codegen tasks (per-module, when the project applies `dev.androidbroadcast.featured`):**

- `generateConfigParam` — typed `ConfigParam` objects + `ConfigValues` extensions
- `generateFeaturedProguardRules` — R8 `-assumevalues` rules for local flags
- `generateIosConstVal` / `generateXcconfig` — Swift DCE inputs
- `generateFeaturedManifest` — emits `featured-manifest.json` consumed by the aggregator
- `generateFeaturedRegistry` (aggregator-only) — produces `GeneratedFeaturedRegistry.kt`

## Plugin Architecture (highest-leverage to understand)

Two plugins, two roles:

| Plugin ID | Where | Role |
|---|---|---|
| `dev.androidbroadcast.featured` | every feature / library module that declares flags | Exposes the `featured { }` DSL; generates per-module `ConfigParam` objects, observe extensions, ProGuard rules, iOS const-val + xcconfig, and a `featured-manifest.json` artifact (consumable Gradle variant `featured-manifest`). |
| `dev.androidbroadcast.featured.application` | the app / aggregator module only | Adds a `featuredAggregation` `dependencyScope` configuration. Resolves the `featured-manifest` variant from each declared project dep, merges them, and generates `GeneratedFeaturedRegistry.all`. **Min Gradle 8.5+** (uses `dependencyScope` / `resolvable` API). |

**Enum-flag classpath gotcha.** `featuredAggregation(project(":foo"))` only pulls the manifest variant — not `:foo`'s compile classpath. If `:foo` declares an `enum` flag whose enum type lives in `:foo`, the aggregator module must also declare `implementation(project(":foo"))` so the enum class is visible at compile time. Primitive-only modules need no extra dependency.

**Auto-wiring policy.** The aggregator does **not** auto-wire its output into a source set — the consumer module wires it manually because the plugin can't safely assume KMP vs. AGP vs. plain JVM:

```kotlin
kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(
    tasks.named("generateFeaturedRegistry").map { it.outputs.files.singleFile.parentFile }
)
```

## Multi-Module Pattern (canonical, demonstrated in `:sample`)

Real apps with N feature modules wire **N production `ConfigValues`** (one per feature), all sharing one `LocalConfigValueProvider`. The shell additionally builds one extra `ConfigValues` for `FeatureFlagsDebugScreen` (Android only in the sample; Desktop/iOS omit the debug surface). Each feature module:

1. Declares its flags in its own `build.gradle.kts: featured { localFlags { … } }`.
2. Exposes public `*FlagObservers.kt` extensions on `ConfigValues` (the only sanctioned cross-module API surface).
3. Owns its own `*FlagsViewModel` taking only its own `ConfigValues`.

`GeneratedLocalFlagsX` / `GeneratedRemoteFlagsX` are `internal` to their module — never reference them across module boundaries. Use `GeneratedFeaturedRegistry.all` for cross-module flag listing.

For non-reactive reads (logging, eager-conditional paths) use `configValues.getValueCached(param)` — the generated `isFooEnabled()` / `getFoo()` extensions are non-suspend and delegate to it (PR #201 restored this synchronous path; R8 DCE depends on it).

## Project Conventions

- **Explicit API mode** is on for every KMP module — all public declarations need explicit visibility. Generated flag objects are deliberately `internal`.
- **Version catalog** (`gradle/libs.versions.toml`) is the single source of truth for dependency versions.
- **Spotless / ktlint** runs over `**/*.kt` and `**/*.kts` excluding `build/`. CI fails on `spotlessCheck`.
- **Public-API stability is reviewed manually in PRs** — there is no automated Binary Compatibility Validator gate (BCV was removed in #150). Reviewers check public-surface changes by hand. Featured has **no migration window** for breaking changes; breaking changes go in directly, the version number reflects it.
- **Branching:** `develop` is the integration branch; PRs go to `develop`, not `main`. `main` is updated only on releases. One logical change per PR — do not bundle.
- **Comment language:** English (per `.github/copilot-instructions.md`).
- **iOS:** SKIE is applied in `:core`; the XCFramework is named `FeaturedCore`. SKIE config is `skie.toml` at repo root.
- **R8:** the project relies on `android.enableR8.fullMode=true` and `android.r8.strictInputValidation=true`. The generated ProGuard rules + `-assumevalues` are what make DCE work.

## Where to Look First When…

- "Find how the DSL is parsed" → `featured-gradle-plugin/src/main/kotlin/.../FeaturedExtension.kt`, `FlagSpec.kt`, `FlagContainer.kt`.
- "Find codegen output shape" → `ConfigParamGenerator.kt`, `ExtensionFunctionGenerator.kt`, `ProguardRulesGenerator.kt`, `XcconfigGenerator.kt`, `IosConstValGenerator.kt` (all in `featured-gradle-plugin/src/main/kotlin/`).
- "Find aggregator wiring" → `FeaturedApplicationPlugin.kt` + `aggregation/` subpackage.
- "Find manifest format" → `manifest/` subpackage (`GenerateFeaturedManifestTask.kt`, `SCHEMA_VERSION`).
- "Verify R8 DCE behaviour" → `featured-shrinker-tests/` (integration tests over real `assembleRelease`).
- Sample wiring → `sample/android-app`, `sample/desktop`, `iosApp/`; per-feature flag observers → `sample/feature-*/.../*FlagObservers.kt`.
