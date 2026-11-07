---
type: spec
slug: flag-stripping-runbook
date: 2026-11-07
status: draft
platform: [android, ios, jvm]
surfaces: [sample, docs]
risk_areas: [compatibility]
non_functional: []
acceptance_criteria_ids: [AC-1, AC-2, AC-3, AC-4, AC-5, AC-6]
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

The chosen behavior is the smallest sample change that makes a real absence on all three apps, plus a runbook that names the command, the artifact, and what absence means. Library runtime does not change. The runbook must not say Android or Desktop cannot prove stripping, and it must not narrow the check to `iosApp`.

The GitHub wiki repository is not available. The page lives in this repo so the issue can be closed from it.

## Promised behavior

`docs/flag-stripping-runbook.md` is the page a person follows. It names every Gradle or Xcode command below. A reader does not invent a task name, an artifact path, or an inspection command.

The interactive `observe()` checkout UI stays. `NewCheckoutBanner` in `iosApp/iosApp/ContentView.swift` stays. The new proof is a pair of sample classes for Android and Desktop, and the existing Swift string for iOS.

Both classes live in the sample, not in a published library module:

- `dev.androidbroadcast.featured.sample.NewCheckoutDisabledBranch`
- `dev.androidbroadcast.featured.sample.NewCheckoutLiveBranch`

A kept shell entry calls both the same way: it consumes the result of a method on the class. The only difference is the predicate.

- The disabled branch is reached only through `configValues.isNewCheckoutEnabled()`, the accessor generated for `:sample:feature-checkout` `new_checkout`.
- The live branch is reached through a read that the generated `-assumevalues` rule does not name. It must not call `isNewCheckoutEnabled()`.

Android reaches that entry from `MainActivity`. Desktop reaches it from `dev.androidbroadcast.featured.MainDesktop.main`. Neither call rewrites `FeaturedSample`, `CheckoutFlagObservers`, or the checkout ViewModel.

`:sample:feature-checkout` declares `discard("dev.androidbroadcast.featured.sample.NewCheckoutDisabledBranch")` on `new_checkout`. It does not discard the live branch.

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

`./gradlew :sample:android-app:assembleRelease`

Artifacts:

- `sample/android-app/build/outputs/apk/release/android-app-release-unsigned.apk`
- `sample/android-app/build/outputs/mapping/release/mapping.txt`

The module has no signing config, so the unsigned APK is the release artifact. The generated `-assumevalues` and `-checkdiscard` files reach that R8 run through the existing consumer ProGuard wiring from `:sample:feature-checkout`. The sample does not paste a hand-written `-assumevalues` rule.

`assembleRelease` succeeds. `mapping.txt` does not contain `dev.androidbroadcast.featured.sample.NewCheckoutDisabledBranch`. It does contain `dev.androidbroadcast.featured.sample.NewCheckoutLiveBranch`. That absence means `new_checkout` was stripped. The live class shows R8 did not delete the sample. This is not `ReleaseDceGuaranteeTest` and not `:featured-shrinker-tests:test`.

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

`jar tf` of that jar does not list `dev/androidbroadcast/featured/sample/NewCheckoutDisabledBranch.class` and does list `dev/androidbroadcast/featured/sample/NewCheckoutLiveBranch.class`. That absence means the flag was stripped. This is not `JvmFlagStrippingTest`.

If that ProGuard run keeps the disabled branch while the live branch is present, the slice is not done. The fix stays in the sample wiring of the generated rules file. It does not change the plugin rule text and does not substitute the library JVM test.

## Public contract

Published library API, generated rule text, and runtime flag resolution do not change.

The sample contract this page checks is:

| Check | Name |
|---|---|
| Disabled class | `dev.androidbroadcast.featured.sample.NewCheckoutDisabledBranch` |
| Live class | `dev.androidbroadcast.featured.sample.NewCheckoutLiveBranch` |
| Accessor | `isNewCheckoutEnabled()` on `ConfigValues`, generated for `new_checkout` |
| iOS string | `New checkout is enabled` |
| Discard spec | `discard("dev.androidbroadcast.featured.sample.NewCheckoutDisabledBranch")` on `new_checkout` only |

## Compatibility

Library versioning is unchanged. This is not an Android public-API change and does not move the API-compat baseline.

Debug behavior of the interactive sample stays. iOS Debug without `DISABLE_NEW_CHECKOUT` still contains the banner string. Android release minification stays enabled. Desktop `packageDistributionForCurrentOS` stays unshrunk; only the release uber-jar task is the proof.

`-checkdiscard` is used only on the Android R8 path, through the existing `discard(...)` DSL. It is not fed to Compose ProGuard.

## Check

The check is a person following `docs/flag-stripping-runbook.md` and seeing:

1. iOS Release executable lacks `New checkout is enabled`; the Debug executable without the generated token still has it.
2. `:sample:android-app:assembleRelease` succeeds; `mapping.txt` lacks the disabled class and contains the live class.
3. `:sample:desktop:packageReleaseUberJarForCurrentOS` writes a `-release.jar` that lacks the disabled class file and contains the live class file.

Issue #213 is closed from that page, not from this spec and not from a library test.

### What is not an acceptable check

- `ReleaseDceGuaranteeTest`, `JvmFlagStrippingTest`, `SwiftFlagStrippingTest`, or `:featured-shrinker-tests:test` described as the sample-app check.
- Absence of an unused accessor. That is unused-code elimination, not proof of `-assumevalues`.
- A page that says the Android or Desktop artifact does not prove stripping.
- `packageDistributionForCurrentOS`, `minifyRelease`, or a hand-run R8 process outside `:sample:desktop:proguardReleaseJars`.
- `:shared:generateXcconfig` or `:sample:shared:generateXcconfig`.
- The checked-in `iosApp/Configuration/FeatureFlags.generated.xcconfig` as the generated token source.

## Acceptance criteria

- [ ] **AC-1.** `docs/flag-stripping-runbook.md` contains the three procedures above, including the commands, artifact paths, inspection commands, and what absence means. It does not rename those tasks.
- [ ] **AC-2.** iOS source for `NewCheckoutBanner` is unchanged. The Release executable lacks `New checkout is enabled` when built with the tokens from `:sample:feature-checkout:generateXcconfig`. The Debug executable built without `DISABLE_NEW_CHECKOUT` still contains that string.
- [ ] **AC-3.** The observe-based checkout UI is unchanged. `MainActivity` and `MainDesktop.main` reach the disabled class only through `isNewCheckoutEnabled()`, and reach the live class without that accessor.
- [ ] **AC-4.** `:sample:android-app:assembleRelease` succeeds. `mapping.txt` lacks `NewCheckoutDisabledBranch` and contains `NewCheckoutLiveBranch`. `new_checkout` discards only the disabled class.
- [ ] **AC-5.** `:sample:desktop:packageReleaseUberJarForCurrentOS` produces a `-release.jar` that lacks `NewCheckoutDisabledBranch.class` and contains `NewCheckoutLiveBranch.class`. Its ProGuard configuration includes `proguard-featured.pro` and does not include `proguard-featured-checkdiscard.pro`.
- [ ] **AC-6.** No published library module, generated rule text, or runtime flag-resolution behavior changes. `CHANGELOG.md` records a sample verification path, not a library behavior change.

## Decisions

- All three apps stay in the runbook. The promise is not narrowed to iOS.
- The absence must be real. A disclaimer that Android or Desktop cannot prove stripping is not this spec.
- Library runtime stays unchanged. The sample may gain the marker, the live control, the `discard(...)` declaration, and Desktop release ProGuard configuration.
- The page is `docs/flag-stripping-runbook.md` because the wiki repository is not available.
- Commands and paths above are closed. Implementation does not substitute a different Gradle task.

## Out of scope

- A GitHub Actions job that runs the runbook.
- Rewriting the interactive flag UI.
- Changing plugin generators, `-assumevalues` text, or published API.
- Using a library test as the sample check.
- Device screenshots.
