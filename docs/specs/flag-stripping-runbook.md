---
type: spec
slug: flag-stripping-runbook
date: 2026-11-07
status: draft
platform: [android, ios, jvm]
surfaces: [sample, docs]
risk_areas: [compatibility]
non_functional: []
acceptance_criteria_ids: [AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8]
design:
---

# Spec: Sample flag-stripping runbook

Date: 2026-11-07
Status: draft
Slug: flag-stripping-runbook

GitHub issue: https://github.com/androidbroadcast/Featured/issues/213

## Context and Motivation

A reader cannot yet follow a written path that shows a disabled local flag was stripped from a sample app. `ReleaseDceGuaranteeTest`, `JvmFlagStrippingTest`, `SwiftFlagStrippingTest`, and `:featured-shrinker-tests:test` prove the generators and shrinker harnesses. They are not the sample-app check.

The current sample does not give Android or Desktop an absence that proves stripping. `:sample:feature-checkout` declares `new_checkout` with `default = false`, but the interactive UI calls `newCheckoutFlow()` / `observe()`, not `isNewCheckoutEnabled()`. Release R8 therefore keeps that UI. `:sample:desktop`'s default distribution task does not run ProGuard. iOS already has a guarded Swift banner and needs no UI rewrite.

The chosen behavior is the smallest sample change that makes a real absence on all three apps, plus a runbook that names the command, the artifact, and what absence means. For Android and Desktop the same disabled class must also be present in a named artifact built without folding `isNewCheckoutEnabled()`. A sibling class in the release artifact is not that control. Library runtime does not change. The runbook must not say Android or Desktop cannot prove stripping, and it must not narrow the check to `iosApp`.

The GitHub wiki repository is not available. The page lives in this repo so the issue can be closed from it.

## Promised behavior

`docs/flag-stripping-runbook.md` is the page a person follows. It names every Gradle or Xcode command below. A reader does not invent a task name, an artifact path, or an inspection command.

The interactive `observe()` checkout UI stays. `NewCheckoutBanner` in `iosApp/iosApp/ContentView.swift` stays. Android and Desktop prove stripping with one class, not a surviving sibling. iOS proves it with the existing Swift string.

`dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch` is `internal` in `:sample:feature-checkout`. It is not a published library type. Its only reference is inside the true branch of a public bridge in that same module:

`public fun ConfigValues.reachNewCheckoutDisabledBranch(): Int` in `CheckoutFlagObservers.kt`.

The bridge calls `isNewCheckoutEnabled()` — the internal accessor generated for `new_checkout` — and, only in that true branch, consumes a method result from `NewCheckoutDisabledBranch`. The false branch returns a constant and does not reference the class. The accessor call is not inside `NewCheckoutDisabledBranch`. Putting it there would keep the class, and the release mapping and jar checks would fail.

`isNewCheckoutEnabled()` stays `internal`. The sample does not set `generation { visibility }` to public and does not add a manual import of the generated declaration from another module. `MainActivity` and `MainDesktop.main` call `reachNewCheckoutDisabledBranch()` on their checkout `ConfigValues` and use the returned `Int`. They do not call `isNewCheckoutEnabled()` and do not reference `NewCheckoutDisabledBranch`. Existing `newCheckoutFlow()`, `checkoutVariantFlow()`, and `setNewCheckout()` stay. `FeaturedSample` and the checkout ViewModel stay.

`:sample:feature-checkout` declares `discard("dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch")` on `new_checkout`. It discards no other class.

### iOS

No source rewrite of the checkout UI.

1. `./gradlew :sample:feature-checkout:generateXcconfig`
2. Read `sample/feature-checkout/build/featured/FeatureFlags.generated.xcconfig`. Its `SWIFT_ACTIVE_COMPILATION_CONDITIONS` line includes `DISABLE_NEW_CHECKOUT`.
3. Release, using the repository Xcode command plus the generated tokens and a fixed derived-data path:

```bash
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build \
  CODE_SIGNING_ALLOWED=NO \
  SWIFT_ACTIVE_COMPILATION_CONDITIONS='$(inherited) <tokens from the generated line>'
```

4. Debug control, same command with `-configuration Debug` and without `DISABLE_NEW_CHECKOUT`.

Do not use `:shared:generateXcconfig` or `:sample:shared:generateXcconfig`. Do not treat the checked-in `iosApp/Configuration/FeatureFlags.generated.xcconfig` as generated output. Release still includes `Config.xcconfig`, so the generated tokens are applied on the `xcodebuild` command line.

The Xcode build phase already runs `:sample:shared:embedAndSignAppleFrameworkForXcode`. The runbook does not add another framework task.

The app is the `.app` under `iosApp/build/Build/Products/Release-iphonesimulator/` and the matching Debug products directory. Inspect the Mach-O executable inside that bundle, the file whose name is the bundle name without `.app`. Do not search the whole bundle.

Absence of the string `New checkout is enabled` from the Release executable, while the Debug executable built without that token still contains it, means `NewCheckoutBanner` was stripped. `strings` on that executable is the inspection command.

### Android

Absence:

```bash
./gradlew :sample:android-app:assembleRelease
```

Artifacts:

- `sample/android-app/build/outputs/apk/release/android-app-release-unsigned.apk`
- `sample/android-app/build/outputs/mapping/release/mapping.txt`

The module has no signing config, so the unsigned APK is the release artifact. The generated `-assumevalues` and `-checkdiscard` files reach that R8 run through the existing consumer ProGuard wiring from `:sample:feature-checkout`. The sample does not paste a hand-written `-assumevalues` rule.

`assembleRelease` succeeds. `mapping.txt` does not contain `dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch`. That absence means `new_checkout` was stripped. This is not `ReleaseDceGuaranteeTest` and not `:featured-shrinker-tests:test`.

`assembleDebug` is not the control. Debug does not run R8, so a present class there does not mean the accessor was not folded. A second minified app variant is not the control either: every `:sample:feature-checkout` Android variant publishes the generated rules as consumer ProGuard files, so a minified app variant still folds the accessor and still applies `-checkdiscard`.

Control, registered by `:sample:android-app` under this exact name:

```bash
./gradlew :sample:android-app:r8UnfoldedMarker
```

Artifact: `sample/android-app/build/featured/r8-unfolded-marker.jar`

The task runs R8 `com.android.tools:r8:9.1.31` from the version catalog, class-file output. Its program input is the release compilation of `:sample:android-app` and the runtime classpath that contains the feature-checkout bridge and `NewCheckoutDisabledBranch`. It keeps `dev.androidbroadcast.featured.sample.MainActivity`. It uses `proguard-android-optimize.txt`. It does not take `proguard-featured.pro` or `proguard-featured-checkdiscard.pro`, and it does not keep `NewCheckoutDisabledBranch`.

`jar tf` of that jar lists `dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class`. That presence means `isNewCheckoutEnabled()` was not folded. If the class is missing here, the release mapping absence does not prove `-assumevalues`.

### Desktop

Compose Multiplatform 1.12.2, the version on `develop`, already registers a release build type with ProGuard enabled. The tasks are not invented:

- `:sample:desktop:proguardReleaseJars`
- `:sample:desktop:packageReleaseUberJarForCurrentOS`

`packageDistributionForCurrentOS` stays the unshrunk default build type. The runbook does not inspect it and does not tell the reader to run `minifyRelease`.

The sample adds the output of `:sample:feature-checkout:generateFeaturedProguardRules` (`sample/feature-checkout/build/featured/proguard-featured.pro`) to `compose.desktop.application.buildTypes.release.proguard.configurationFiles`, with a task dependency so `proguardReleaseJars` sees that file. It does not add `proguard-featured-checkdiscard.pro`. Compose release ProGuard is Guardsquare ProGuard 7.8, which honors `-assumevalues` and does not accept R8 `-checkdiscard`. `obfuscate` stays at its default `false`, so the class file name remains the inspection key.

```bash
./gradlew :sample:desktop:packageReleaseUberJarForCurrentOS
```

The artifact is the jar that task writes under `sample/desktop/build/compose/jars/`, the file whose name ends in `-release.jar`. The task log line `The jar is written to` is that path.

`jar tf` of that jar does not list `dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class`. That absence means the flag was stripped. This is not `JvmFlagStrippingTest`.

`packageDistributionForCurrentOS` and `packageUberJarForCurrentOS` are not the control. Those default tasks do not run ProGuard, so a present class there does not mean the accessor was not folded. Compose 1.12.2 has no third build type to register, and release ProGuard cannot omit the rules file once `configurationFiles` includes it.

Control, registered by `:sample:desktop` under this exact name:

```bash
./gradlew :sample:desktop:proguardUnfoldedMarker
```

Artifact: `sample/desktop/build/featured/proguard-unfolded-marker.jar`

The task runs ProGuard 7.8.0, the Compose 1.12.2 release default, on the same input jars as `:sample:desktop:proguardReleaseJars`. Optimization is on and obfuscation is off. It keeps `dev.androidbroadcast.featured.MainDesktop`. It does not take `proguard-featured.pro` or `proguard-featured-checkdiscard.pro`, and it does not keep `NewCheckoutDisabledBranch`.

`jar tf` of that jar lists `dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class`. That presence means `isNewCheckoutEnabled()` was not folded. If the class is missing here, the release jar absence does not prove `-assumevalues`. The fix for a failed release absence stays in the sample wiring of the generated rules file. It does not change the plugin rule text and does not substitute the library JVM test.

## Public contract

Published library API, generated rule text, and runtime flag resolution do not change.

The sample contract this page checks is:

| Check | Name |
|---|---|
| Disabled class | `internal` `dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch` in `:sample:feature-checkout` |
| Bridge | `public fun ConfigValues.reachNewCheckoutDisabledBranch(): Int` in `CheckoutFlagObservers.kt` |
| Accessor | `internal` `isNewCheckoutEnabled()` on `ConfigValues`, called only from that bridge |
| iOS string | `New checkout is enabled` |
| Discard spec | `discard("dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch")` on `new_checkout` only |
| Android control task | `:sample:android-app:r8UnfoldedMarker` |
| Desktop control task | `:sample:desktop:proguardUnfoldedMarker` |

## Compatibility

Library versioning is unchanged. This is not an Android public-API change and does not move the API-compat baseline.

Debug behavior of the interactive sample stays. iOS Debug without `DISABLE_NEW_CHECKOUT` still contains the banner string. Android release minification stays enabled. Desktop `packageDistributionForCurrentOS` stays unshrunk; the release uber-jar task is the Desktop absence proof.

Generated accessors stay `internal`, as `AGENTS.md` requires. Shells call the feature module's public bridge. They do not reach into `:sample:feature-checkout`'s generated declarations.

`-checkdiscard` is used only on the Android release R8 path, through the existing `discard(...)` DSL. It is not an input of either unfolded control task, and it is not fed to Compose ProGuard. A present class in an unfolded jar must not fail `assembleRelease`.

## Check

The check is a person following `docs/flag-stripping-runbook.md` and seeing:

1. iOS Release executable lacks `New checkout is enabled`; the Debug executable without the generated token still has that same string.
2. `:sample:android-app:assembleRelease` succeeds; `mapping.txt` lacks `NewCheckoutDisabledBranch`. `:sample:android-app:r8UnfoldedMarker` writes a jar that contains that same class. Presence there means `isNewCheckoutEnabled()` was not folded.
3. `:sample:desktop:packageReleaseUberJarForCurrentOS` writes a `-release.jar` that lacks `NewCheckoutDisabledBranch.class`. `:sample:desktop:proguardUnfoldedMarker` writes a jar that contains that same class file. Presence there means `isNewCheckoutEnabled()` was not folded.

Issue #213 is closed from that page, not from this spec and not from a library test.

### What is not an acceptable check

- `ReleaseDceGuaranteeTest`, `JvmFlagStrippingTest`, `SwiftFlagStrippingTest`, or `:featured-shrinker-tests:test` described as the sample-app check.
- Absence of an unused accessor. That is unused-code elimination, not proof of `-assumevalues`.
- A sibling class in the same release artifact. That shows shrinking did not delete the sample. It does not show `NewCheckoutDisabledBranch` present when the accessor was not folded.
- `assembleDebug`, `packageDistributionForCurrentOS`, or `packageUberJarForCurrentOS` as the control. Those builds do not fold or not-fold the accessor; they do not run the release shrinker.
- A page that says the Android or Desktop artifact does not prove stripping.
- `packageDistributionForCurrentOS`, `minifyRelease`, or a hand-run R8 process outside `:sample:desktop:proguardReleaseJars`.
- `:shared:generateXcconfig` or `:sample:shared:generateXcconfig`.
- The checked-in `iosApp/Configuration/FeatureFlags.generated.xcconfig` as the generated token source.

## Acceptance criteria

- [ ] **AC-1.** `docs/flag-stripping-runbook.md` contains the absence procedures and the unfolded controls above, including the commands, artifact paths, inspection commands, and what absence and presence mean. It does not rename those tasks.
- [ ] **AC-2.** iOS source for `NewCheckoutBanner` is unchanged. The Release executable lacks `New checkout is enabled` when built with the tokens from `:sample:feature-checkout:generateXcconfig`. The Debug executable built without `DISABLE_NEW_CHECKOUT` still contains that string.
- [ ] **AC-3.** `newCheckoutFlow()`, `checkoutVariantFlow()`, `setNewCheckout()`, `FeaturedSample`, and the checkout ViewModel are unchanged. `reachNewCheckoutDisabledBranch()` is the only new public bridge. It is the only caller of `isNewCheckoutEnabled()`. `NewCheckoutDisabledBranch` is referenced only from that function's true branch. `MainActivity` and `MainDesktop.main` call the bridge and do not call the generated accessor.
- [ ] **AC-4.** `:sample:android-app:assembleRelease` succeeds. `mapping.txt` lacks `NewCheckoutDisabledBranch`. `new_checkout` discards only that class.
- [ ] **AC-5.** `:sample:desktop:packageReleaseUberJarForCurrentOS` produces a `-release.jar` that lacks `NewCheckoutDisabledBranch.class`. Its ProGuard configuration includes `proguard-featured.pro` and does not include `proguard-featured-checkdiscard.pro`.
- [ ] **AC-6.** `:sample:android-app:r8UnfoldedMarker` writes `sample/android-app/build/featured/r8-unfolded-marker.jar`, and that jar contains `NewCheckoutDisabledBranch.class`. The task does not take the generated rules files.
- [ ] **AC-7.** `:sample:desktop:proguardUnfoldedMarker` writes `sample/desktop/build/featured/proguard-unfolded-marker.jar`, and that jar contains `NewCheckoutDisabledBranch.class`. The task does not take the generated rules files.
- [ ] **AC-8.** No published library module, generated rule text, generated accessor visibility, or runtime flag-resolution behavior changes. `CHANGELOG.md` records a sample verification path, not a library behavior change.

## Decisions

- All three apps stay in the runbook. The promise is not narrowed to iOS.
- The absence must be real. A disclaimer that Android or Desktop cannot prove stripping is not this spec.
- Library runtime stays unchanged. The sample may gain the disabled class, the feature-module bridge, the `discard(...)` declaration, Desktop release ProGuard configuration, and the two unfolded control tasks.
- The control is the same disabled class, present when the accessor is not folded. A sibling class in the release artifact is not that control.
- The accessor call stays in `:sample:feature-checkout`. Shells call the public bridge. That is the `AGENTS.md` ownership rule, not a published accessor.
- The page is `docs/flag-stripping-runbook.md` because the wiki repository is not available.
- Commands and paths above are closed. Implementation does not substitute a different Gradle task.

## Out of scope

- A GitHub Actions job that runs the runbook.
- Rewriting the interactive flag UI.
- Changing plugin generators, `-assumevalues` text, or published API.
- Using a library test as the sample check.
- Device screenshots.
