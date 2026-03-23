# AGP 9 Migration Design

**Date:** 2026-03-22
**Target:** Single PR — full AGP 9 support, no legacy mode as end state

---

## Context

The Featured project is a Kotlin Multiplatform (KMP) configuration management library supporting
Android, iOS (via SKIE), and JVM.

### Current versions

| Component | Current | Target |
|---|---|---|
| Android Gradle Plugin | 8.12.0 | 9.1.0 |
| Gradle wrapper | 8.14.3 | 9.1.0 |
| Kotlin | 2.3.10 | 2.3.10 (unchanged) |
| Dokka | 2.0.0 | 2.1.0 |
| SKIE | 0.10.10 | 0.10.10 (unchanged, compatibility risk — see below) |
| Compose Multiplatform | 1.9.3 | 1.9.3 (unchanged) |

### Module inventory

**KMP + Android library modules** (require plugin migration):
- `core` ⚠️ SKIE applied
- `featured-testing`, `featured-platform`
- `featured-compose`, `featured-debug-ui`
- `datastore-provider` ⚠️ SKIE applied
- `featured-registry`

**Android-only library modules** (no plugin change — keep `com.android.library`):
- `firebase-provider` (uses Firebase BOM)
- `sharedpreferences-provider`

**KMP sample app** (requires module restructuring):
- `sample`

**Unaffected modules** (no Android dependency):
- `javaprefs-provider`, `nsuserdefaults-provider`, `featured-bom`,
  `featured-detekt-rules`, `featured-gradle-plugin`

---

## Migration strategy

The PR lands full AGP 9 support. `android.enableLegacyVariantApi=true` is used **temporarily**
in Issue 1 so all parallel agents work in a compiling codebase — it is removed in Issue 6 before
the PR is merged. `android.builtInKotlin` is NOT set to false at any point: the
`com.android.kotlin.multiplatform.library` plugin requires `builtInKotlin=true` (the AGP 9
default) to function.

Exception: if a specific plugin (e.g. SKIE) cannot be migrated, document the blocker with an
inline comment and retain the minimal flag scoped to that module only.

---

## Known risk: SKIE compatibility

`co.touchlab.skie:gradle-plugin:0.10.10` is the latest stable release. SKIE hooks into the
Kotlin compiler plugin API and the Android Gradle Plugin lifecycle. Its compatibility with
`com.android.kotlin.multiplatform.library` (the AGP 9 KMP library plugin) has **not been
confirmed**.

SKIE is applied to `core` (Issue 2) and `datastore-provider` (Issue 4).

**Contingency:** if SKIE fails to initialise with the new plugin, those two modules retain
`com.android.library` + `androidTarget { }` and document the blocker inline:
```kotlin
// TODO: Migrate to com.android.kotlin.multiplatform.library once SKIE supports AGP 9
// Tracking: https://github.com/touchlab/SKIE/issues/???
```
All other modules proceed with full migration.

---

## Issue breakdown

### Issue 1 — Foundation (sequential, must complete first)

All other issues branch from this one.

**Changes:**

| File | Change |
|---|---|
| `gradle/wrapper/gradle-wrapper.properties` | `8.14.3` → `9.1.0` |
| `gradle/libs.versions.toml` | `agp = "8.12.0"` → `"9.1.0"` |
| `gradle/libs.versions.toml` | `dokka = "2.0.0"` → `"2.1.0"` |
| `gradle/libs.versions.toml` | Add `androidKmpLibrary` plugin alias |
| `build.gradle.kts` (root) | Add `alias(libs.plugins.androidKmpLibrary) apply false` |
| `gradle.properties` | Add `org.gradle.parallel=true` |
| `gradle.properties` | Add temporary `android.enableLegacyVariantApi=true` |

**New version catalog entry:**
```toml
androidKmpLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
```

**Note on `android.builtInKotlin`:** do NOT set it to `false`. The AGP 9 default is `true`,
which is required by the new KMP library plugin. Android-only modules that still declare
`org.jetbrains.kotlin.android` are compatible with `builtInKotlin=true` — the plugin becomes
redundant rather than conflicting.

**Verify:** run `./gradlew help` (configuration only) to confirm the project configures without
errors after version bumps. Build failures in module compilation are expected at this stage — they
will be resolved by Issues 2–5.

**Dokka 2.1 note:** confirm that `org.jetbrains.dokka.experimental.gradle.pluginMode=V1Enabled`
remains a valid property in Dokka 2.1 (the property name and accepted values changed between
major Dokka releases). If not valid, update or remove the property as part of this issue.

---

### Issues 2–5 — KMP module migrations (parallel, depend on Issue 1)

Each issue migrates its module group from the old two-block DSL to the new unified `androidLibrary`
DSL. Android-only modules (`firebase-provider`, `sharedpreferences-provider`) are not in scope.

#### Canonical DSL migration pattern

**Before:**
```kotlin
plugins {
    alias(libs.plugins.androidLibrary)      // com.android.library
    alias(libs.plugins.kotlinMultiplatform)
}

android {
    namespace = "dev.androidbroadcast.featured.example"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
```

**After:**
```kotlin
plugins {
    alias(libs.plugins.androidKmpLibrary)   // com.android.kotlin.multiplatform.library
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    androidLibrary {
        namespace = "dev.androidbroadcast.featured.example"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
```

Also rename test dependency configuration in every migrated module:
```kotlin
// Before
androidUnitTestImplementation(...)
// After
androidHostTestImplementation(...)
```

#### Issue 2 — `core`, `featured-testing`, `featured-platform`

No Compose dependencies. Straightforward plugin + DSL swap.

⚠️ **`core` carries SKIE.** Apply the SKIE contingency if the build fails after migration:
retain `com.android.library` + `androidTarget { }` for `core` only, document the blocker inline,
and proceed with `featured-testing` and `featured-platform`.

#### Issue 3 — `featured-compose`, `featured-debug-ui`

Same plugin + DSL swap. These modules carry Compose Multiplatform dependencies — verify Compose
source sets compile correctly after migration.

#### Issue 4 — `datastore-provider`, `featured-registry`

Same plugin + DSL swap.

⚠️ **`datastore-provider` carries SKIE.** Apply the SKIE contingency if the build fails:
retain the old plugin for `datastore-provider` only, document inline, proceed with
`featured-registry`.

#### Issue 5 — `sample` module restructuring

AGP 9 requires separating the Android entry point from shared KMP code for application modules.

**New structure:**
```
sample/            # Shared KMP code — keeps existing KMP plugins, composeHotReload removed
  src/commonMain/
  src/iosMain/
  ...

androidApp/        # New module — Android entry point only
  src/main/
    AndroidManifest.xml
    kotlin/
      MainActivity.kt   (moved from sample/src/androidMain/)
  build.gradle.kts      # com.android.application + org.jetbrains.kotlin.android
                        # + composeMultiplatform + composeCompiler + composeHotReload
```

**`iosApp/` is unaffected.** It references the XCFramework produced by the `sample` KMP module,
which continues to live in `sample/`. The XCFramework artifact name and path do not change.
`Package.swift` (if present at repo root) does not require updates.

**`composeHotReload` moves to `androidApp/`.** It is a JVM/Android-only feature; remove it from
`sample/build.gradle.kts` and add it to `androidApp/build.gradle.kts`.

`androidApp` declares a dependency on `sample`:
```kotlin
dependencies {
    implementation(project(":sample"))
}
```

Add `androidApp` to `settings.gradle.kts`.

---

### Issue 6 — Final cleanup + verification (sequential, depends on Issues 2–5)

#### Remove temporary flag

Remove from `gradle.properties`:
```properties
android.enableLegacyVariantApi=true
```

#### Android-only modules: built-in Kotlin

`firebase-provider` and `sharedpreferences-provider` retain `org.jetbrains.kotlin.android` for
now — it is compatible with `android.builtInKotlin=true` (redundant, not conflicting) and
removing it would require migrating `kotlin { explicitApi() }` and `jvmToolchain(21)` to AGP
equivalents, which is out of scope for this PR. This is acceptable cleanup for a future PR.

#### Firebase BOM and `android.dependency.useConstraints=false`

`firebase-provider` uses `platform(libs.firebase.bom)`. Setting `useConstraints=false` disables
AGP's automatic constraint injection between configurations but does not affect how BOM-based
version resolution works within the module's own dependency graph. Firebase BOM resolution is
handled by Gradle's platform dependency mechanism, which is independent of AGP constraints.
This setting is safe for `firebase-provider`.

#### Final `gradle.properties`

Replace the entire file with explicit declarations — no implicit defaults:

```properties
# Kotlin
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx3072M

# Gradle
org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true
org.gradle.parallel=true

# Android — General
android.useAndroidX=true
android.nonTransitiveRClass=true
android.uniquePackageNames=true
android.enableJetifier=false
android.sdk.defaultTargetSdkToCompileSdkIfUnset=true

# Android — DSL & Kotlin
android.builtInKotlin=true
android.newDsl=true

# Android — Dependencies
android.dependency.useConstraints=false

# Android — Build Features
android.defaults.buildfeatures.resvalues=false

# Android — Testing
android.default.androidx.test.runner=true
android.onlyEnableUnitTestForTheTestedBuildType=true

# Android — R8 / ProGuard
android.enableR8.fullMode=true
android.r8.optimizedResourceShrinking=true
android.r8.strictInputValidation=true
android.proguard.failOnMissingFiles=true

# Publishing
VERSION_NAME=0.1.0-SNAPSHOT

# Dokka
org.jetbrains.dokka.experimental.gradle.pluginMode=V1Enabled
```

#### Build verification

Run `./gradlew build`. Fix any errors. If a specific plugin blocks full migration, document the
blocker with an inline comment and keep only the minimum required flag scoped to that plugin.

All tests must pass before the PR is raised.

---

## Parallelisation plan

```
Issue 1 (Foundation — version bumps + temporary legacy flag)
    ├── Issue 2 (core ⚠️SKIE, featured-testing, featured-platform)
    ├── Issue 3 (featured-compose, featured-debug-ui)
    ├── Issue 4 (datastore-provider ⚠️SKIE, featured-registry)
    └── Issue 5 (sample restructuring → androidApp extraction)
            └── Issue 6 (Final cleanup + gradle.properties + verification)
```

Issues 2–5 run in parallel worktrees after Issue 1 merges into the feature branch.
Issue 6 runs after Issues 2–5 are all merged.

---

## Properties reference

| Property | AGP 8 default | AGP 9 default | Value chosen | Reason |
|---|---|---|---|---|
| `android.builtInKotlin` | `false` | `true` | `true` | Required by new KMP plugin; Kotlin 2.3.10 via existing KGP classpath |
| `android.newDsl` | `false` | `true` | `true` | Full migration; no legacy DSL |
| `android.uniquePackageNames` | `false` | `true` | `true` | All modules already have unique namespaces |
| `android.enableLegacyVariantApi` | `false` | `false` | not set | Removed before merge; no legacy variant API used |
| `android.dependency.useConstraints` | `true` | `false` | `false` | Library; let consuming app decide; Firebase BOM unaffected |
| `android.onlyEnableUnitTestForTheTestedBuildType` | `false` | `true` | `true` | Reduces test task count across 10+ modules |
| `android.sdk.defaultTargetSdkToCompileSdkIfUnset` | `false` | `true` | `true` | Safe; all modules already declare explicit values |
| `android.defaults.buildfeatures.resvalues` | `true` | removed | `false` | No module uses resValues |
| `android.nonTransitiveRClass` | — | — | `true` | Already set; modern practice |
| `android.useAndroidX` | — | — | `true` | Already set |
| `android.enableJetifier` | — | — | `false` | Using AndroidX directly; no support libs |
| `android.enableR8.fullMode` | — | `true` | `true` | Full R8 optimisation |
| `android.r8.optimizedResourceShrinking` | `false` | `true` | `true` | Better resource shrinking |
| `android.r8.strictInputValidation` | `false` | `true` | `true` | Catch broken keep rules early |
| `android.proguard.failOnMissingFiles` | `false` | `true` | `true` | Fail fast on missing ProGuard files |
| `android.default.androidx.test.runner` | `false` | `true` | `true` | Standard AndroidX test runner |
