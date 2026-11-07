---
type: spec
slug: appmetrica-provider
date: 2026-11-07
status: draft
platform: [android]
surfaces: [library]
risk_areas: [compatibility]
non_functional: [ci]
acceptance_criteria_ids: [AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8]
design:
---

# Spec: AppMetrica remote provider

Date: 2026-11-07
Status: draft
Slug: appmetrica-provider

GitHub issue: https://github.com/androidbroadcast/Featured/issues/204

## Context and Motivation

Callers in the RU/CIS market use AppMetrica as the default remote-config backend. This slice adds one Android remote provider for that backend and stops there.

Issue #204 names `io.appmetrica.analytics:analytics-remote-config`. That coordinate is not published. Maven Central has no such artifact through AppMetrica `8.6.0`, and `appmetrica/appmetrica-sdk-android` has no remote-config module. `RemoteConfig*` types in that repository are the SDK's own server configuration, not a flag client.

The published AppMetrica flag SDK is Varioqub. The official Android integration is `Varioqub.init(settings, AppMetricaAdapter(context), context)`, then `fetchConfig`, `activateConfig`, and `getString` / `getValue`:

https://yandex.ru/support/varioqub-app/ru/sdk/android/integration

This spec binds the provider to that SDK. It does not keep the unpublished coordinate.

## Promised behavior

An Android library, `:providers:appmetrica`, implements `RemoteConfigValueProvider` and `InitializableConfigValueProvider` over Varioqub. Its public shape matches `providers/firebase/`: one provider class, a mutable converter registry, a single exception type, and no second provider API.

The app owns vendor startup. The provider does not call `Varioqub.init` or `AppMetrica.activate`. The caller initializes AppMetrica, then calls `Varioqub.init` with an `AppMetricaAdapter`, then uses this provider to read and refresh flags.

`get` reads the current activated value. `initialize` loads the stored configuration without a network fetch. `fetch` downloads a new configuration and activates it only when asked.

Supported reads are `Boolean`, `String`, `Int`, `Long`, `Double`, and `Float`. Enums resolve by constant `name` when no converter is registered. No other vendor is added. iOS is not in this slice.

## Public contract

Package: `dev.androidbroadcast.featured.appmetrica`.

Published artifact: `dev.androidbroadcast.featured:featured-appmetrica-provider`.

Pinned SDK versions, in the version catalog:

| Coordinate | Version | Used by |
|---|---|---|
| `com.yandex.varioqub:config` | `0.10.0` | provider, to call `VarioqubApi` |
| `com.yandex.varioqub:appmetrica-adapter` | `0.10.0` | sample and documented app init, not the provider class |
| `io.appmetrica.analytics:analytics` | `8.6.0` | sample, so `AppMetrica.activate` and the adapter's runtime types resolve |

`8.6.0` is the newest stable `io.appmetrica.analytics:analytics` on Maven Central as of this spec. The `0.10.0` adapter calls `AppMetrica`, `ModulesFacade`, `IModuleReporter`, and `StartupParamsCallback`. If `8.6.0` does not compile against that adapter, pin the newest stable analytics version that does. Do not pin the stale docs example `7.10.0`.

### Provider

```kotlin
public class AppMetricaConfigValueProvider(
    client: VarioqubApi = Varioqub.getInstance(),
) : RemoteConfigValueProvider, InitializableConfigValueProvider
```

`VarioqubApi` and `Varioqub` are `com.yandex.varioqub.config` types from `config` `0.10.0`. The no-arg constructor uses the process singleton. Tests inject a mock.

The provider does not call `Varioqub.init`, `AppMetrica.activate`, `Varioqub.setDefaults`, or `Varioqub.fetchConfig` except as specified under `fetch`. It does not push `ConfigParam.defaultValue` into the SDK.

`public val converters: Converters` is a mutable registry, same shape as Firebase:

- `set` / `get` / `getValue` / `contains` by `KClass`
- `put` is a public inline reified helper
- the constructor of `Converters` is not public
- a later `put` for the same type replaces the previous converter

`public fun interface Converter<T : Any>` converts `com.yandex.varioqub.config.model.ConfigValue`. It is not the core `TypeConverter` used by local providers. Built-in converters are registered for `String`, `Boolean`, `Int`, `Long`, `Double`, and `Float`.

`public class AppMetricaConfigException(message: String, cause: Throwable) : Exception`. `cause` is non-null. This is the only public failure type. Fetch and initialize failures use it. `CancellationException` is not wrapped and is not a subtype of this exception.

### get

`get` calls `client.getValue(param.key)` and returns a non-null `dev.androidbroadcast.featured.ConfigValue`. It does not return null for a missing key, and it does not substitute `ConfigParam.defaultValue`. `ConfigValues` applies that default only when a provider returns null.

The value is converted from the Varioqub `ConfigValue` (`asString`, `asBoolean`, `asLong`, `asDouble`). `Int` is `asLong()` when the long is inside `Int.MIN_VALUE..Int.MAX_VALUE`; otherwise `IllegalArgumentException`. `Float` is `asDouble().toFloat()` when the double is finite; `NaN` and infinities throw `IllegalArgumentException`.

An enum with no registered converter uses the string value as the constant `name`. An unknown name throws `IllegalArgumentException`. A non-enum type with no converter throws `IllegalStateException`. A registered converter wins over the built-in and over enum-name resolution, and a converter that cannot parse throws `IllegalArgumentException`.

Source mapping, from `ConfigValue.getSourceType()` on `config` `0.10.0`:

| Varioqub source | Int | Featured `ConfigValue.Source` |
|---|---|---|
| `SOURCE_SERVER` | 2 | `REMOTE` |
| `SOURCE_INAPP_DEFAULT` | 1 | `REMOTE_DEFAULT` |
| `SOURCE_DEFAULT` | 0 | `DEFAULT` |
| any other | | `UNKNOWN` |

### initialize

`initialize` calls `client.activateConfig` exactly once and completes when its callback runs. It does not call `fetchConfig`. It does not perform a network fetch. The callback has no error channel; a callback invocation is success.

If `activateConfig` throws `CancellationException`, it propagates. Any other exception from the call is wrapped in `AppMetricaConfigException` with that exception as `cause`. Cancelling the waiting coroutine also propagates `CancellationException` and must not resume later as `AppMetricaConfigException`.

### fetch

`fetch` uses the interface default `activate = true`.

- `activate = true`: call `fetchConfig`. On `onSuccess`, call `activateConfig` and return when that callback runs. On `onError`, do not call `activateConfig`.
- `activate = false`: call `fetchConfig` and return on `onSuccess`. Do not call `activateConfig`.

`onError` throws `AppMetricaConfigException`. The message contains the listener message and the `FetchError` name. `cause` is non-null. `REQUEST_THROTTLED` is a failure, not success. The provider does not retry and does not sleep.

`CancellationException` from the client call, or from cancelling the waiting coroutine, propagates unwrapped.

### Module and publication metadata

`:providers:appmetrica` is `com.android.library`, included from `settings.gradle.kts`. Namespace `dev.androidbroadcast.featured.appmetrica`. `minSdk` and `compileSdk` come from the catalog (`minSdk` 24). Java 17. Main source sets compile with `-Xexplicit-api=strict`. It applies `mavenPublish`, Dokka, Kover, and `dev.androidbroadcast.featured.internal.android-api-compat`, same as `:providers:firebase`.

The provider's implementation dependency is `com.yandex.varioqub:config:0.10.0` plus `:core`. It does not depend on `appmetrica-adapter` or `io.appmetrica.analytics:analytics`, because the provider class does not reference them.

POM coordinates, license, developer, and SCM match the other providers. Artifact id `featured-appmetrica-provider`. Name `Featured AppMetrica Provider`. Description says it reads remote flags through Varioqub for AppMetrica. `featured-bom` constrains the project.

`consumer-rules.pro` keeps the names of `com.yandex.varioqub.config.**` and `dev.androidbroadcast.featured.appmetrica.**`, the same kind of rule `:providers:firebase` ships. No extra R8 rules.

This pull request does not publish to Maven Central and does not change `VERSION_NAME`. Publication stays with the release process.

### Android API gate

The new artifact is published Android surface, so it joins the gate the same way `:providers:firebase` does. Register it in `AndroidApiGate` and in `docs/specs/api-compat-gate.md`:

| Gradle project | Published artifact | Baseline |
|---|---|---|
| `:providers:appmetrica` | `dev.androidbroadcast.featured:featured-appmetrica-provider` | `providers/appmetrica/api/android/featured-appmetrica-provider.api` |

The baseline is generated by the dumper `checkAndroidApi` already uses. A hand-written or empty file does not count. Update the gate tests that currently assert the nine-module list. No other module enters the gate in this slice.

### Sample

`sample/android-app` depends on `:providers:appmetrica`, `com.yandex.varioqub:appmetrica-adapter:0.10.0`, and `io.appmetrica.analytics:analytics` at the pinned version.

One `AppMetricaConfigValueProvider` is passed as `remoteProvider` on the existing `ConfigValues` instances in `MainActivity`: checkout, promotions, ui, and debug. Those instances keep the shared local provider. Do not add another `ConfigValues` graph.

An `Application`, registered in the sample manifest, shows the official startup order when both secrets are present:

1. `AppMetrica.activate(context, AppMetricaConfig.newConfigBuilder(apiKey).build())`
2. `Varioqub.init(VarioqubSettings.Builder(clientId).build(), AppMetricaAdapter(context), context)`

`apiKey` and `clientId` come from `local.properties` (`appmetrica.apiKey`, `appmetrica.clientId`). `clientId` is the AppMetrica app id in the form `appmetrica.XXXXXX`. If either value is missing or blank, the sample does not call `activate` or `init`. It still compiles and still constructs the provider. No key, placeholder key, or `google-services`-style secret file is committed. `local.properties` stays untracked.

### Wiki and changelog

Update the Featured wiki page [Providers](https://github.com/AndroidBroadcast/Featured/wiki/Providers). Under built-in remote providers, add `AppMetricaConfigValueProvider` in the same shape as the Firebase section:

- artifact `featured-appmetrica-provider`
- supported types, including enums by name
- constructor, default and with an injected `VarioqubApi`
- `fetch()` activates immediately; `fetch(activate = false)` does not
- `initialize()` is `activateConfig` only, no network
- the app calls `AppMetrica.activate` and `Varioqub.init` with `AppMetricaAdapter`; the provider does not
- link the official Android integration page above
- `AppMetricaConfigException` is not retried immediately; Varioqub throttles fetches
- this artifact is Android-only

Also add `featured-appmetrica-provider` to the artifact table and the Android remote-provider snippet on the wiki [Installation](https://github.com/AndroidBroadcast/Featured/wiki/Installation) page, next to `featured-firebase-provider`. Do not document the iOS pods there.

`CHANGELOG.md` `[Unreleased]` / `Added` records the artifact, that it is Android-only, and that Maven Central publication waits for the release process. Reference `#204`.

## Compatibility

Android only. `minSdk` 24, the catalog floor, which is above Varioqub's floor of 21. No KMP source set, no iOS target, no Swift package change.

The public Android signatures of this module are stable surface. Adding them is an additive change and needs a baseline update. It does not by itself require a major bump. This slice does not bump `VERSION_NAME`.

iOS Varioqub (`pod Varioqub`, `pod VarioqubAppMetricaAdapter`, https://yandex.ru/support/varioqub-app/ru/sdk/ios/integration) is out of this slice. Do not add an iOS provider or mention it as shipped.

Existing providers, including Firebase, are unchanged. `ConfigValueProvider`, `RemoteConfigValueProvider`, and `InitializableConfigValueProvider` are unchanged.

## Check

The check is the module tests plus the existing CI jobs, not a device run.

`:providers:appmetrica:test` runs on Robolectric with `org.robolectric:robolectric` from the catalog and a mocked `VarioqubApi`. The tests fail if any of these break:

- source mapping for `SOURCE_SERVER`, `SOURCE_INAPP_DEFAULT`, `SOURCE_DEFAULT`, and an unknown source int
- `String`, `Boolean`, `Int`, `Long`, `Double`, and `Float` reads
- `Int` outside the 32-bit range and non-finite `Float` throw `IllegalArgumentException`
- enum by name, unknown enum name, registered converter replacing the built-in, unregistered non-enum type
- `initialize` calls `activateConfig` once and does not call `fetchConfig`
- `fetch(true)` calls `fetchConfig` then `activateConfig`; `fetch(false)` calls `fetchConfig` only; `fetch()` defaults to activate
- `onError`, including `REQUEST_THROTTLED`, throws `AppMetricaConfigException` and does not activate
- `CancellationException` from initialize and fetch is not wrapped

`:providers:appmetrica:koverVerify` is in the CI test job. The line-coverage bound is at least 70. Do not copy Firebase's placeholder bound of 0.

`checkAndroidApi` passes with the generated baseline committed and fails if that baseline is missing. `:sample:android-app:assembleDebug` compiles the wiring, including the init path, without a committed key. `spotlessCheck` passes.

The Providers and Installation wiki pages and `CHANGELOG.md` contain the text above. This pull request does not run the Maven Central publish workflow.

## Out of scope

- Publishing `featured-appmetrica-provider` to Maven Central
- iOS, desktop, or a KMP provider
- LaunchDarkly, GrowthBook, Unleash, PostHog, Statsig, Split, Amplitude, or any other vendor
- A second provider shape, a local provider, or changes to `ConfigValues` resolution
- Calling `Varioqub.init` or `AppMetrica.activate` from the provider
- Device screenshots or a logged-in AppMetrica project
- A version bump or release tag

## Decisions

- The SDK is Varioqub `config` `0.10.0`, not `io.appmetrica.analytics:analytics-remote-config`.
- The adapter `0.10.0` is pinned for app init. The provider class does not depend on it.
- The public shape is the Firebase provider's shape, including honoring `fetch(activate)`.
- The sample shows official init and does not commit a key.
- The module is publishable and gated like the other Android providers, and is not published in this pull request.
