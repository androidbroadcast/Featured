# Sample flag-stripping runbook

This page is how a person checks that a disabled local flag was stripped from a sample app. Follow the commands below. Do not invent a Gradle task, an artifact path, or an inspection command.

`new_checkout` is a local boolean in `:sample:feature-checkout` whose default is `false`. The interactive `observe()` checkout UI stays. This page does not change library runtime behavior, and a green library suite does not show this check.

Issue: https://github.com/androidbroadcast/Featured/issues/213

## What absence and presence mean

| App | Absence | Control |
|---|---|---|
| iOS | Release `Featured` executable lacks `New checkout is enabled` | Debug executable, built without `DISABLE_NEW_CHECKOUT`, still contains that string |
| Android | `assembleRelease` succeeds and `mapping.txt` lacks `NewCheckoutDisabledBranch` | `:sample:android-app:r8UnfoldedMarker` writes a jar that contains that class |
| Desktop | The `-release.jar` lacks `NewCheckoutDisabledBranch.class` | `:sample:desktop:proguardUnfoldedMarker` writes a jar that contains that class |

Android and Desktop absence means `new_checkout` was stripped. Presence in the unfolded jar means `isNewCheckoutEnabled()` was not folded. If that class is missing from the unfolded jar, the release absence does not prove `-assumevalues`.

The disabled class is `internal` `dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch` in `:sample:feature-checkout`. The only caller of the generated accessor is `ConfigValues.reachNewCheckoutDisabledBranch()` in that module. `MainActivity.onCreate` and `MainDesktop.main` call that bridge and use the returned `Int`. They do not call `isNewCheckoutEnabled()`.

## Not an acceptable check

- `ReleaseDceGuaranteeTest`, `JvmFlagStrippingTest`, `SwiftFlagStrippingTest`, or `:featured-shrinker-tests:test` as the sample-app check.
- Absence of an unused accessor. That is unused-code elimination, not proof of `-assumevalues`.
- A sibling class in the same release artifact. That shows shrinking did not delete the sample. It does not show `NewCheckoutDisabledBranch` present when the accessor was not folded.
- `assembleDebug`, `packageDistributionForCurrentOS`, or `packageUberJarForCurrentOS` as the control. Those builds do not run the release shrinker.
- A claim that the Android or Desktop artifact does not prove stripping.
- `minifyRelease`, or a hand-run R8 process outside `:sample:desktop:proguardReleaseJars`.
- `:shared:generateXcconfig` or `:sample:shared:generateXcconfig`.
- The checked-in `iosApp/Configuration/FeatureFlags.generated.xcconfig` as the generated token source.

## iOS

No source change to `NewCheckoutBanner`. The existing `#if !DISABLE_NEW_CHECKOUT` wraps the call. An optimized Release compile drops `New checkout is enabled` when the token is set and keeps it when the token is absent.

1. Generate the tokens from the checkout module, not from the shared aggregator:

```bash
./gradlew :sample:feature-checkout:generateXcconfig
```

2. Read `sample/feature-checkout/build/featured/FeatureFlags.generated.xcconfig`. The `SWIFT_ACTIVE_COMPILATION_CONDITIONS` line includes `DISABLE_NEW_CHECKOUT`. Do not treat the checked-in placeholder as that file.

3. Release. Derived data is `iosApp/build`. Code signing is off. Release still includes `Config.xcconfig`, so `PRODUCT_NAME` is `Featured`. The compilation-conditions override is single-quoted so the shell does not expand `$(inherited)`. The tokens are the ones from the generated line:

```bash
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build \
  CODE_SIGNING_ALLOWED=NO \
  SWIFT_ACTIVE_COMPILATION_CONDITIONS='$(inherited) DISABLE_NEW_CHECKOUT'
```

4. Debug control. Same command, Debug configuration, no `DISABLE_NEW_CHECKOUT` override:

```bash
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build \
  CODE_SIGNING_ALLOWED=NO
```

The app is `iosApp/build/Build/Products/Release-iphonesimulator/Featured.app` and `iosApp/build/Build/Products/Debug-iphonesimulator/Featured.app`. Inspect the Mach-O named `Featured` inside that bundle, not the whole bundle:

```bash
strings iosApp/build/Build/Products/Release-iphonesimulator/Featured.app/Featured
strings iosApp/build/Build/Products/Debug-iphonesimulator/Featured.app/Featured
```

Absence of `New checkout is enabled` in the Release executable, and presence of that string in the Debug executable, means `NewCheckoutBanner` was stripped.

## Android

Absence:

```bash
./gradlew :sample:android-app:assembleRelease
```

Artifacts:

- `sample/android-app/build/outputs/apk/release/android-app-release-unsigned.apk` (the module has no signing config)
- `sample/android-app/build/outputs/mapping/release/mapping.txt`

The generated `-assumevalues` and `-checkdiscard` files reach that R8 run through the existing consumer ProGuard wiring from `:sample:feature-checkout`. This page does not add a hand-written `-assumevalues` rule.

```bash
grep 'dev.androidbroadcast.featured.sample.checkout.NewCheckoutDisabledBranch' \
  sample/android-app/build/outputs/mapping/release/mapping.txt
```

`assembleRelease` succeeds, and that `grep` prints nothing, means `new_checkout` was stripped. Search the class name, not the simple name alone: the bridge method `reachNewCheckoutDisabledBranch` contains that simple name and can remain in the line mapping after the class is gone. This is not `ReleaseDceGuaranteeTest` and not `:featured-shrinker-tests:test`.

`assembleDebug` is not the control. Debug does not run R8.

Control. This task does not replace `assembleRelease`:

```bash
./gradlew :sample:android-app:r8UnfoldedMarker
```

Artifact: `sample/android-app/build/featured/r8-unfolded-marker.jar`

The task runs R8 `com.android.tools:r8:9.1.31` from the version catalog, class-file output, on the release compilation of `:sample:android-app` and the runtime classpath jar that contains the bridge and `NewCheckoutDisabledBranch`. That input is pre-R8. It is not the unsigned APK. It keeps `public void onCreate(android.os.Bundle)` and the actual `protected` JVM override on `dev.androidbroadcast.featured.sample.MainActivity`, uses `proguard-android-optimize.txt`, and does not take `proguard-featured.pro` or `proguard-featured-checkdiscard.pro`. It does not keep `NewCheckoutDisabledBranch`. Names are not minified on this task only, so `jar tf` still shows the original class file. Optimization is off on this task only: class-file R8 otherwise replaces `onCreate` with `throw null` and the entry-point keep would not retain the call. Release `mapping.txt` remains the absence check, because release still optimizes and obfuscates.

```bash
jar tf sample/android-app/build/featured/r8-unfolded-marker.jar
```

That listing includes `dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class`. That presence means `isNewCheckoutEnabled()` was not folded. If the class is missing here, the release mapping absence does not prove `-assumevalues`.

## Desktop

`:sample:desktop` release ProGuard is already enabled by Compose Multiplatform 1.12.2. The tasks are `proguardReleaseJars` and `packageReleaseUberJarForCurrentOS`. Do not run `minifyRelease`. Do not inspect `packageDistributionForCurrentOS` or `packageUberJarForCurrentOS`. Those default tasks do not run ProGuard.

Release ProGuard is Guardsquare ProGuard 7.8.0. The sample adds `sample/feature-checkout/build/featured/proguard-featured.pro` (the output of `:sample:feature-checkout:generateFeaturedProguardRules`) to `compose.desktop.application.buildTypes.release.proguard.configurationFiles`. It does not add `proguard-featured-checkdiscard.pro`. Obfuscation stays off, so the class file name remains the inspection key.

Absence:

```bash
./gradlew :sample:desktop:packageReleaseUberJarForCurrentOS
```

The artifact is the jar that task writes under `sample/desktop/build/compose/jars/`, the file whose name ends in `-release.jar`. The task log line `The jar is written to` is that path.

```bash
jar tf <path from The jar is written to>
```

`jar tf` does not list `dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class`. That absence means the flag was stripped. This is not `JvmFlagStrippingTest`.

Control. This task does not replace the release uber jar:

```bash
./gradlew :sample:desktop:proguardUnfoldedMarker
```

Artifact: `sample/desktop/build/featured/proguard-unfolded-marker.jar`

The task runs ProGuard 7.8.0 on the same input jars as `:sample:desktop:proguardReleaseJars`. Optimization is on and obfuscation is off. It keeps `public static void main(java.lang.String[])` on `dev.androidbroadcast.featured.MainDesktop`. It includes Compose `default-compose-desktop-rules.pro`. It does not take `proguard-featured.pro` or `proguard-featured-checkdiscard.pro`, and it does not keep `NewCheckoutDisabledBranch`.

```bash
jar tf sample/desktop/build/featured/proguard-unfolded-marker.jar
```

That listing includes `dev/androidbroadcast/featured/sample/checkout/NewCheckoutDisabledBranch.class`. That presence means `isNewCheckoutEnabled()` was not folded. If the class is missing here, the release jar absence does not prove `-assumevalues`.
