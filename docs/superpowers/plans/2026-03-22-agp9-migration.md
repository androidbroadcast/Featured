# AGP 9 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Featured KMP project from AGP 8.12.0 + Gradle 8.14.3 to AGP 9.1.0 + Gradle 9.1.0 with full (non-legacy) AGP 9 support in a single PR.

**Architecture:** Issue 1 lays the foundation (version bumps + temporary legacy flag). Issues 2–5 migrate KMP module groups in parallel worktrees. Issue 6 removes the temporary flag, rewrites `gradle.properties` with all explicit properties, and verifies the full build.

**Tech Stack:** Kotlin 2.3.10, AGP 9.1.0, Gradle 9.1.0, Kotlin Multiplatform, SKIE 0.10.10, Compose Multiplatform 1.9.3

**Spec:** `docs/superpowers/specs/2026-03-22-agp9-migration-design.md`

---

## Parallelisation Map

```
Task 1: Foundation  (sequential — all other tasks depend on this)
    ├── Task 2: core, featured-testing, featured-platform
    ├── Task 3: featured-compose, featured-debug-ui
    ├── Task 4: datastore-provider, featured-registry
    └── Task 5: sample → androidApp restructuring
            └── Task 6: Final cleanup + full verification
```

Tasks 2–5 run in **parallel worktrees** off the Task 1 branch. Task 6 runs after all four merge.

---

## GitHub Issues to Create (before starting implementation)

Create one GitHub issue per task below using `gh issue create`. Each issue becomes the PR description for its worktree branch.

| Issue | Title | Assignee |
|---|---|---|
| Task 1 | `build: upgrade Gradle 9.1.0 + AGP 9.1.0 + Dokka 2.1.0 foundation` | — |
| Task 2 | `build: migrate core, featured-testing, featured-platform to AGP 9 KMP plugin` | — |
| Task 3 | `build: migrate featured-compose, featured-debug-ui to AGP 9 KMP plugin` | — |
| Task 4 | `build: migrate datastore-provider, featured-registry to AGP 9 KMP plugin` | — |
| Task 5 | `build: extract androidApp module from sample for AGP 9 compliance` | — |
| Task 6 | `build: finalize AGP 9 migration — remove legacy flags, explicit gradle.properties` | — |

---

## Task 1: Foundation — Version Bumps + Temporary Compatibility Flag

**Branch:** `feat/agp9-migration` off `main`

**Files to modify:**
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/libs.versions.toml`
- `build.gradle.kts` (root)
- `gradle.properties`

---

- [ ] **Step 1.1: Create the feature branch**

```bash
git checkout main && git pull
git checkout -b feat/agp9-migration
```

- [ ] **Step 1.2: Upgrade the Gradle wrapper**

Edit `gradle/wrapper/gradle-wrapper.properties` — change the `distributionUrl` line:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip
```

- [ ] **Step 1.3: Upgrade AGP, Dokka, and add the KMP library plugin alias**

Edit `gradle/libs.versions.toml`:

```toml
# Change these two lines:
agp = "9.1.0"
dokka = "2.1.0"

# Add this line in the [plugins] section:
androidKmpLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
```

- [ ] **Step 1.4: Register the new plugin in the root build file**

Edit `build.gradle.kts` (root). In the `plugins {}` block, add **after** the existing
`alias(libs.plugins.androidLibrary) apply false` line — do not remove any existing entries:

```kotlin
alias(libs.plugins.androidKmpLibrary) apply false
```

- [ ] **Step 1.5: Add parallel build and temporary compatibility flags to gradle.properties**

Edit `gradle.properties` — append:

```properties
# Build performance
org.gradle.parallel=true

# AGP 9 migration — temporary: removed in Task 6 once all modules are migrated
android.enableLegacyVariantApi=true
```

> ⚠️ Do NOT set `android.builtInKotlin=false`. The new `com.android.kotlin.multiplatform.library`
> plugin requires `builtInKotlin=true` (the AGP 9 default). The existing `kotlinAndroid` plugin
> on android-only modules is compatible with `builtInKotlin=true` — redundant, not conflicting.

- [ ] **Step 1.6: Verify Dokka 2.1 property compatibility**

Check whether `org.jetbrains.dokka.experimental.gradle.pluginMode=V1Enabled` is still valid in
Dokka 2.1 by running:

```bash
./gradlew :core:dokkaHtml --dry-run 2>&1 | head -40
```

If you see an error about `V1Enabled` being an unknown value, remove the property from
`gradle.properties`. If `dokkaHtml` configures cleanly, keep it.

- [ ] **Step 1.7: Verify project configures**

```bash
./gradlew help 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Configuration-time errors must be fixed before continuing.
Compilation failures in individual modules are expected at this point and will be fixed in Tasks 2–5.

- [ ] **Step 1.8: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties \
        gradle/libs.versions.toml \
        build.gradle.kts \
        gradle.properties
git commit -m "build: upgrade Gradle 9.1.0 + AGP 9.1.0 + Dokka 2.1.0 — migration foundation"
```

---

## Task 2: Migrate `core`, `featured-testing`, `featured-platform`

**Branch:** `feat/agp9-task2-core-group` off `feat/agp9-migration` (Task 1 branch)

**Files to modify:**
- `core/build.gradle.kts`
- `featured-testing/build.gradle.kts`
- `featured-platform/build.gradle.kts`

**⚠️ SKIE risk:** `core` applies `alias(libs.plugins.skie)`. If SKIE 0.10.10 does not support
`com.android.kotlin.multiplatform.library`, the `core` migration will fail at compile time.
Follow the contingency steps below.

---

### Migrate `featured-testing`

- [ ] **Step 2.1: Apply DSL migration to `featured-testing/build.gradle.kts`**

Replace the plugin declaration and remove the `android {}` block:

```kotlin
// BEFORE (plugins block):
alias(libs.plugins.androidLibrary)
alias(libs.plugins.kotlinMultiplatform)

// AFTER (plugins block):
alias(libs.plugins.androidKmpLibrary)
alias(libs.plugins.kotlinMultiplatform)
```

Move `android {}` configuration into `kotlin { androidLibrary {} }` and remove the old blocks:

```kotlin
// REMOVE the entire android { } block
// REMOVE androidTarget { } from inside kotlin { }

// ADD inside kotlin { } replacing androidTarget { }:
androidLibrary {
    namespace = "dev.androidbroadcast.featured.testing"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
```

Rename test dependency if present:
```kotlin
// androidUnitTestImplementation(...) → androidHostTestImplementation(...)
```

- [ ] **Step 2.2: Verify `featured-testing` compiles**

```bash
./gradlew :featured-testing:compileKotlinAndroid 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

### Migrate `featured-platform`

- [ ] **Step 2.3: Apply DSL migration to `featured-platform/build.gradle.kts`**

Same pattern as `featured-testing` — swap plugin alias, collapse `android {}` into
`kotlin { androidLibrary {} }`, rename any `androidUnitTestImplementation` to
`androidHostTestImplementation`.

- [ ] **Step 2.4: Verify `featured-platform` compiles**

```bash
./gradlew :featured-platform:compileKotlinAndroid 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

### Migrate `core` (SKIE module)

- [ ] **Step 2.5: Apply DSL migration to `core/build.gradle.kts`**

Swap plugin alias and collapse blocks — same pattern as above. The `skie {}` configuration block
and the `zipXCFramework` task remain unchanged.

```kotlin
// BEFORE (plugins block):
alias(libs.plugins.kotlinMultiplatform)
alias(libs.plugins.androidLibrary)
alias(libs.plugins.skie)
...

// AFTER (plugins block):
alias(libs.plugins.kotlinMultiplatform)
alias(libs.plugins.androidKmpLibrary)
alias(libs.plugins.skie)
...
```

Replace `androidTarget { compilerOptions { } }` with `androidLibrary { ... }` inside `kotlin {}`,
and remove the `android {}` block:

```kotlin
kotlin {
    explicitApi()

    androidLibrary {
        namespace = "dev.androidbroadcast.featured.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    val xcf = XCFramework("FeaturedCore")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FeaturedCore"
            isStatic = true
            xcf.add(this)
        }
    }

    jvm()

    sourceSets { /* unchanged */ }
}
// Remove android { } block entirely
// Keep skie { } block unchanged
```

- [ ] **Step 2.6: Test SKIE compatibility — verify `core` compiles**

```bash
./gradlew :core:compileKotlinAndroid 2>&1 | tail -40
```

**If `BUILD SUCCESSFUL`:** SKIE is compatible. Continue.

**If build fails with a SKIE-related error** (e.g., `SKIEPlugin`, `co.touchlab.skie`, plugin
application error): apply the contingency — revert `core/build.gradle.kts` to its pre-migration
state and add this comment at the top:

```kotlin
// TODO: Migrate to androidKmpLibrary once SKIE supports AGP 9.
// Tracking: https://github.com/touchlab/SKIE/issues (search for AGP 9 support)
// Blocked by: co.touchlab.skie:gradle-plugin:0.10.10 incompatible with
//             com.android.kotlin.multiplatform.library
```

Then run again to confirm the contingency builds:
```bash
./gradlew :core:compileKotlinAndroid 2>&1 | tail -20
```

- [ ] **Step 2.7: Run all tests for this group**

```bash
./gradlew :core:allTests :featured-testing:allTests :featured-platform:allTests 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. Use `allTests` rather than `test` — some KMP modules may not have
a JVM target and therefore no plain `test` task.

- [ ] **Step 2.8: Commit**

```bash
git add core/build.gradle.kts featured-testing/build.gradle.kts featured-platform/build.gradle.kts
git commit -m "build: migrate core, featured-testing, featured-platform to AGP 9 KMP plugin"
```

---

## Task 3: Migrate `featured-compose`, `featured-debug-ui`

**Branch:** `feat/agp9-task3-compose-group` off `feat/agp9-migration` (Task 1 branch)

**Files to modify:**
- `featured-compose/build.gradle.kts`
- `featured-debug-ui/build.gradle.kts`

---

- [ ] **Step 3.1: Apply DSL migration to `featured-compose/build.gradle.kts`**

Swap plugin alias (`androidLibrary` → `androidKmpLibrary`), collapse `android {}` into
`kotlin { androidLibrary {} }`. This module uses Compose Multiplatform — source set structure
and dependencies are unchanged; only the plugin and DSL block change.

- [ ] **Step 3.2: Verify `featured-compose` compiles**

```bash
./gradlew :featured-compose:compileKotlinAndroid 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. If Compose-specific errors appear, verify the Compose source sets
(`commonMain`, `androidMain`) are still declared correctly inside `kotlin { sourceSets { } }`.

- [ ] **Step 3.3: Apply DSL migration to `featured-debug-ui/build.gradle.kts`**

Same pattern as `featured-compose`.

- [ ] **Step 3.4: Verify `featured-debug-ui` compiles**

```bash
./gradlew :featured-debug-ui:compileKotlinAndroid 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.5: Run tests**

```bash
./gradlew :featured-compose:allTests :featured-debug-ui:allTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.6: Commit**

```bash
git add featured-compose/build.gradle.kts featured-debug-ui/build.gradle.kts
git commit -m "build: migrate featured-compose, featured-debug-ui to AGP 9 KMP plugin"
```

---

## Task 4: Migrate `datastore-provider`, `featured-registry`

**Branch:** `feat/agp9-task4-data-group` off `feat/agp9-migration` (Task 1 branch)

**Files to modify:**
- `datastore-provider/build.gradle.kts`
- `featured-registry/build.gradle.kts`

**⚠️ SKIE risk:** `datastore-provider` applies SKIE. Same contingency as `core` in Task 2.

---

- [ ] **Step 4.1: Apply DSL migration to `featured-registry/build.gradle.kts`**

Swap plugin alias, collapse blocks. No SKIE involved — straightforward.

- [ ] **Step 4.2: Verify `featured-registry` compiles**

```bash
./gradlew :featured-registry:compileKotlinAndroid 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.3: Apply DSL migration to `datastore-provider/build.gradle.kts`**

Swap plugin alias, collapse blocks. Keep the `skie {}` configuration block unchanged.

- [ ] **Step 4.4: Test SKIE compatibility — verify `datastore-provider` compiles**

```bash
./gradlew :datastore-provider:compileKotlinAndroid 2>&1 | tail -40
```

**If `BUILD SUCCESSFUL`:** SKIE compatible — continue.

**If SKIE-related failure:** revert `datastore-provider/build.gradle.kts` and add the same
contingency comment as in Task 2 Step 2.6.

- [ ] **Step 4.5: Run tests**

```bash
./gradlew :datastore-provider:allTests :featured-registry:allTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.6: Commit**

```bash
git add datastore-provider/build.gradle.kts featured-registry/build.gradle.kts
git commit -m "build: migrate datastore-provider, featured-registry to AGP 9 KMP plugin"
```

---

## Task 5: Extract `androidApp` Module from `sample`

**Branch:** `feat/agp9-task5-sample-split` off `feat/agp9-migration` (Task 1 branch)

**Files to create:**
- `androidApp/build.gradle.kts`
- `androidApp/src/main/AndroidManifest.xml` (copied from `sample/src/androidMain/`)
- `androidApp/src/main/kotlin/dev/androidbroadcast/featured/SampleApplication.kt` (moved)
- `androidApp/src/main/res/**` (moved — full icon/resource tree)

**Files to modify:**
- `settings.gradle.kts` (register `:androidApp` **first** — before writing build.gradle.kts)
- `sample/build.gradle.kts`

**Files unchanged:** `iosApp/` and `Package.swift` — the iOS framework is produced by the KMP
`sample` module which keeps its iOS targets. No path changes.

**⚠️ SKIE risk:** `sample/build.gradle.kts` applies `alias(libs.plugins.skie)`. When `sample`
switches from `androidApplication` to `androidKmpLibrary`, SKIE encounters the same
compatibility risk as `core` and `datastore-provider`. Apply the identical contingency if needed.

---

- [ ] **Step 5.1: Audit all Android-specific files in `sample/src/androidMain/`**

```bash
find sample/src/androidMain -type f | sort
```

Expected output (verified against actual project):
```
sample/src/androidMain/AndroidManifest.xml
sample/src/androidMain/kotlin/dev/androidbroadcast/featured/SampleApplication.kt
sample/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml
sample/src/androidMain/res/drawable/ic_launcher_background.xml
sample/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml
sample/src/androidMain/res/mipmap-anydpi-v26/ic_launcher_round.xml
sample/src/androidMain/res/mipmap-hdpi/ic_launcher.png
sample/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png
sample/src/androidMain/res/mipmap-mdpi/ic_launcher.png
sample/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png
sample/src/androidMain/res/mipmap-xhdpi/ic_launcher.png
sample/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png
sample/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png
sample/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png
sample/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png
sample/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png
sample/src/androidMain/res/values/strings.xml
```

If the output differs, adjust subsequent copy steps to match actual files.

- [ ] **Step 5.2: Create the `androidApp` directory structure**

```bash
mkdir -p androidApp/src/main/kotlin/dev/androidbroadcast/featured
```

- [ ] **Step 5.3: Register `androidApp` in `settings.gradle.kts` (must be done before Step 5.5)**

Add to the include list in `settings.gradle.kts`:

```kotlin
include(":androidApp")
```

Gradle evaluates all subproject build files only after `settings.gradle.kts` is fully parsed.
`:androidApp` must be registered here before its `build.gradle.kts` can be evaluated.

- [ ] **Step 5.4: Copy all Android-specific files to `androidApp`**

```bash
# Manifest
cp sample/src/androidMain/AndroidManifest.xml \
   androidApp/src/main/AndroidManifest.xml

# Kotlin source
cp sample/src/androidMain/kotlin/dev/androidbroadcast/featured/SampleApplication.kt \
   androidApp/src/main/kotlin/dev/androidbroadcast/featured/SampleApplication.kt

# Resources (entire tree)
cp -r sample/src/androidMain/res androidApp/src/main/res
```

Do not delete the originals yet — confirm the new module compiles first (Step 5.8).

- [ ] **Step 5.5: Create `androidApp/build.gradle.kts`**

Note: dependencies use `project(":")` notation to match the existing project convention.
`targetSdk` is explicitly set here (the original `sample` relied on the AGP default;
`androidApp` declares it explicitly as best practice for application modules).

```kotlin
// Set to true (or pass -PhasFirebase=true) when google-services.json is present.
val hasFirebase =
    project.findProperty("hasFirebase") == "true" ||
        rootProject.file("sample/google-services.json").exists()

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "dev.androidbroadcast.featured"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.androidbroadcast.featured"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "HAS_FIREBASE", "$hasFirebase")
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":sample"))

    implementation(compose.preview)
    implementation(libs.androidx.activity.compose)
    implementation(project(":featured-compose"))
    implementation(project(":featured-platform"))
    implementation(project(":sharedpreferences-provider"))

    debugImplementation(compose.uiTooling)
    debugImplementation(project(":featured-debug-ui"))

    if (hasFirebase) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.config)
        implementation(project(":firebase-provider"))
    }
}
```

- [ ] **Step 5.6: Strip Android-specific content from `sample/build.gradle.kts`**

**Plugin changes:**
```kotlin
// Remove:
alias(libs.plugins.androidApplication)   // replaced by androidKmpLibrary
alias(libs.plugins.composeHotReload)     // moved to androidApp
alias(libs.plugins.skie)                 // see SKIE risk below

// Add:
alias(libs.plugins.androidKmpLibrary)
```

Keep: `kotlinMultiplatform`, `composeMultiplatform`, `composeCompiler`.

**⚠️ SKIE on `sample`:** removing `alias(libs.plugins.skie)` is safe only if the iOS framework
(`FeaturedSampleApp`) does not rely on SKIE-generated Swift interop. If the iosApp Swift code
calls any SKIE-bridged API from `sample`, retain SKIE and test the SKIE contingency path below.

**Replace `android {}` block** with `kotlin { androidLibrary {} }`:
```kotlin
androidLibrary {
    namespace = "dev.androidbroadcast.featured.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
```

**Remove** from `kotlin { sourceSets { androidMain.dependencies { } } }` the dependencies that
moved to `androidApp`:
- `compose.preview`
- `libs.androidx.activity.compose`
- `project(":featured-compose")`
- `project(":featured-platform")`
- `project(":sharedpreferences-provider")`
- Firebase entries

**Preserve** the following — they belong to the shared KMP module:
- `jvm()` target and `jvmMain.dependencies { }` block (compose.desktop, coroutinesSwing)
- `compose.desktop { application { } }` block
- `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` targets with `FeaturedSampleApp` framework
- `commonMain.dependencies { }`

**Remove** the top-level `dependencies { debugImplementation(...) }` block — moved to `androidApp`.

- [ ] **Step 5.7: Test SKIE compatibility on `sample` (if SKIE was kept)**

If `alias(libs.plugins.skie)` was retained in `sample/build.gradle.kts`:

```bash
./gradlew :sample:compileKotlinAndroid 2>&1 | tail -40
```

**If failure with SKIE error:** apply contingency — revert `sample` to `androidApplication` +
old DSL; the `androidApp` restructuring cannot proceed while SKIE blocks the new plugin.
Document with:
```kotlin
// TODO: Switch sample to androidKmpLibrary once SKIE supports AGP 9.
// Tracking: https://github.com/touchlab/SKIE/issues
```

- [ ] **Step 5.8: Verify `androidApp` assembles**

```bash
./gradlew :androidApp:assembleDebug 2>&1 | tail -40
```

Expected: `BUILD SUCCESSFUL`.

If resource errors appear (missing launcher icons), verify Step 5.4 copied the full `res/` tree.

- [ ] **Step 5.9: Verify `sample` JVM desktop target still builds**

```bash
./gradlew :sample:jvmJar 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Confirms the `compose.desktop` and `jvmMain` content was preserved.

- [ ] **Step 5.10: Remove original Android-specific files from `sample/src/androidMain/`**

Only after Steps 5.8 and 5.9 both succeed:

```bash
rm sample/src/androidMain/kotlin/dev/androidbroadcast/featured/SampleApplication.kt
rm sample/src/androidMain/AndroidManifest.xml
rm -r sample/src/androidMain/res

# Confirm the androidApp still assembles without the originals
./gradlew :androidApp:assembleDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5.11: Commit**

```bash
git add androidApp/ sample/build.gradle.kts settings.gradle.kts
git commit -m "build: extract androidApp module from sample for AGP 9 KMP app compliance"
```

---

## Task 6: Final Cleanup + Explicit `gradle.properties` + Full Verification

**Branch:** merge Tasks 2–5 into `feat/agp9-migration`, then continue on that branch.

**Files to modify:**
- `gradle.properties` (full rewrite)

---

- [ ] **Step 6.1: Merge all parallel task branches into `feat/agp9-migration`**

```bash
git checkout feat/agp9-migration
git merge feat/agp9-task2-core-group
git merge feat/agp9-task3-compose-group
git merge feat/agp9-task4-data-group
git merge feat/agp9-task5-sample-split
```

Resolve any merge conflicts (unlikely — modules are independent files).

- [ ] **Step 6.2: Rewrite `gradle.properties` with all explicit properties**

Replace the entire file content with:

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

> This replaces the temporary `android.enableLegacyVariantApi=true` and `org.gradle.parallel=true`
> (already added in Task 1) with the complete, permanent set of explicit properties.

- [ ] **Step 6.3: Verify project configures cleanly**

```bash
./gradlew help 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` with no warnings about unknown or deprecated properties.
If `android.defaults.buildfeatures.resvalues` is unrecognised in AGP 9.1.0, remove it,
then re-run `./gradlew help` to confirm the project still configures cleanly.

- [ ] **Step 6.4: Run the full build**

```bash
./gradlew build 2>&1 | tail -50
```

Expected: `BUILD SUCCESSFUL`. Fix any compilation errors before continuing.

Common failure patterns and fixes:
- **"Unresolved reference: androidUnitTestImplementation"** — rename to `androidHostTestImplementation` in the affected module
- **SKIE error on `core` or `datastore-provider`** — apply the contingency from Task 2 / Task 4
- **`enableLegacyVariantApi` error** — a plugin still uses legacy variant API; add `android.enableLegacyVariantApi=true` back with an inline comment naming the plugin

- [ ] **Step 6.5: Run all tests**

```bash
./gradlew allTests 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. All test suites pass. Use `allTests` rather than `test` — KMP
modules without a JVM target have no plain `test` task.

- [ ] **Step 6.6: Run spotless check**

```bash
./gradlew spotlessCheck 2>&1 | tail -20
```

If violations found: `./gradlew spotlessApply` then re-run check.

- [ ] **Step 6.7: Run binary compatibility check**

```bash
./gradlew apiCheck 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Public API must not have changed.

- [ ] **Step 6.8: Commit**

```bash
git add gradle.properties
git commit -m "build: finalize AGP 9 migration — remove legacy flag, explicit gradle.properties"
```

- [ ] **Step 6.9: Open the pull request**

```bash
gh pr create \
  --base main \
  --title "build: migrate to AGP 9.1.0 + Gradle 9.1.0" \
  --body "$(cat <<'EOF'
## Summary

- Upgrades Android Gradle Plugin 8.12.0 → 9.1.0
- Upgrades Gradle wrapper 8.14.3 → 9.1.0
- Upgrades Dokka 2.0.0 → 2.1.0
- Migrates all KMP library modules to `com.android.kotlin.multiplatform.library` plugin with unified `androidLibrary {}` DSL
- Extracts `androidApp` module from `sample` as required by AGP 9 for KMP apps
- Rewrites `gradle.properties` with all AGP 9 properties explicitly declared
- Removes all legacy compatibility flags (full AGP 9 support, no legacy mode)

## Test plan

- [ ] `./gradlew build` passes
- [ ] `./gradlew allTests` passes
- [ ] `./gradlew spotlessCheck` passes
- [ ] `./gradlew apiCheck` passes
- [ ] Android sample app assembles: `./gradlew :androidApp:assembleDebug`
- [ ] iOS framework builds (if Xcode available): `./gradlew :core:assembleXCFramework`

## Notes

If SKIE 0.10.10 is incompatible with the new plugin, `core` and/or `datastore-provider`
retain `com.android.library` temporarily with a `// TODO` comment and tracking issue link.
EOF
)"
```

---

## Reference: Canonical DSL Migration Pattern

For every KMP+Android library module the transformation is:

**Before:**
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)         // ← change this
    // ... other plugins unchanged
}

kotlin {
    androidTarget {                             // ← replace with androidLibrary {}
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    // ... other targets unchanged
}

android {                                       // ← remove entire block
    namespace = "..."
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
```

**After:**
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)      // ← new plugin
    // ... other plugins unchanged
}

kotlin {
    androidLibrary {                           // ← replaces androidTarget {} + android {}
        namespace = "..."
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    // ... other targets unchanged
}
// android {} block is gone
```
