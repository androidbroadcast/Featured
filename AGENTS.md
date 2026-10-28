## Context

Featured is a Kotlin Multiplatform feature-flag and configuration library for Android, iOS, and JVM/Desktop. It combines typed parameters, reactive providers, Compose integrations, and Gradle-generated flag accessors and release-optimization inputs. The `sample/` modules demonstrate the intended multi-module integration.

## Commands and workflow

Run Gradle commands from the repository root using the wrapper. Use JDK 17, matching CI and the JVM toolchains; take Kotlin, Android SDK, and dependency versions from `gradle/libs.versions.toml` and Gradle itself from `gradle/wrapper/gradle-wrapper.properties`.

Run these checks before submitting code changes:

```bash
# JVM/Android unit tests, KMP JVM tests, and the separate plugin build's tests
./gradlew test jvmTest :featured-gradle-plugin:test

# Formatting and Android build/lint gates
./gradlew spotlessCheck
./gradlew assembleDebug lint

# Coverage gates used by CI
./gradlew :core:koverVerify \
  :providers:datastore:koverVerify \
  :providers:sharedpreferences:koverVerify \
  :providers:firebase:koverVerify \
  :featured-compose:koverVerify \
  :featured-testing:koverVerify
```

Use `./gradlew spotlessApply` to format Kotlin and Gradle Kotlin DSL changes. For targeted iterations, use the actual module path and target-specific test task:

```bash
./gradlew :core:jvmTest --tests 'dev.androidbroadcast.featured.ConfigValuesTest'
./gradlew :featured-debug-ui:jvmTest
./gradlew :providers:sharedpreferences:testDebugUnitTest
./gradlew :featured-gradle-plugin:test
./gradlew :featured-shrinker-tests:test
./gradlew :core:koverHtmlReport
```

Do not treat `./gradlew test` as the complete suite: it selects conventional JVM/Android test tasks, not KMP `jvmTest` tasks, and does not select the included build's tests. Use `:module:allTests` for KMP cross-target verification where the host supports the configured native targets.

For Apple-facing changes, run the applicable simulator tests and CI's framework/Xcode checks on macOS:

```bash
./gradlew :core:iosSimulatorArm64Test \
  :providers:nsuserdefaults:iosSimulatorArm64Test
swiftlint lint --strict --config .swiftlint.yml
./gradlew :core:linkDebugFrameworkIosSimulatorArm64 \
  :core:linkDebugFrameworkIosArm64 \
  :providers:datastore:linkDebugFrameworkIosSimulatorArm64 \
  :providers:datastore:linkDebugFrameworkIosArm64
xcodebuild build -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO
```

## Testing instructions

- Put portable runtime and provider tests in `src/commonTest`; put JVM-only cases in `src/jvmTest`, native storage tests in `src/iosTest`, and conventional JVM/Android module tests in `src/test`.
- Use `kotlin.test`, coroutine `runTest`, and Turbine for shared flow tests. Use camelCase test names in shared/native source sets; reserve backtick names for JVM-only tests.
- Cover default/local/remote resolution, value source, override/reset/clear behavior, initialization/fetch, converters, cancellation, and provider failures when changing these contracts. For observation changes, assert emission order/count and source as well as value, including recovery and unrelated-key changes.
- Use `fakeConfigValues { set(param, value) }` or `ConfigValues.fake { ... }` from `featured-testing` for consumer tests. These are suspend factories returning real `ConfigValues` over an in-memory provider, not a `FakeConfigValues` class. Keep this module in test dependencies.
- Use Robolectric for SharedPreferences tests and MockK for Firebase SDK interactions. Keep preference stores isolated and clean them between tests.
- Test plugin generators and DSL validation directly; use Gradle TestKit and `featured-gradle-plugin/src/test/fixtures/` for task wiring, generated compilation, manifests, publication metadata, and aggregation.
- Preserve configuration-cache reuse, build-cache invalidation after DSL changes, and Project Isolation regression coverage when changing plugin inputs or wiring.
- Ensure `ANDROID_HOME` or `ANDROID_SDK_ROOT` is present when validating Android TestKit fixtures. Those tests use assumptions to skip without an SDK; a passing plugin test task alone does not prove Android integration ran.
- Run `featured-shrinker-tests` for changes to generated accessor signatures or R8 rules. These tests synthesize bytecode with ASM, run R8 directly, and inspect output classes/references; plugin TestKit tests separately cover Android app/AAR wiring.
- Preserve the configured line-coverage floors: core 90%, DataStore 70%, SharedPreferences 80%, Compose 65%, and testing helpers 80%. Coverage exclusions and rules live in each module's `build.gradle.kts`.

CI is defined in `.github/workflows/ci.yml`: test/coverage, Android assemble/lint, Spotless, and macOS framework/Xcode checks are separate jobs. CodeQL and dependency review have separate workflows under `.github/workflows/`.

## Project layout and module map

- `settings.gradle.kts` is the authoritative Gradle module list. Use `:providers:datastore`-style project paths, not published artifact names such as `datastore-provider`.
- `core/`: parameter/value descriptors, resolution strategies, provider interfaces, converters, in-memory storage, and runtime contracts. Keep portable behavior in `src/commonMain`; isolate platform logging and other actual implementations in platform source sets.
- `providers/`: storage and remote-service implementations depending on core. DataStore and ConfigCat are KMP; Firebase and SharedPreferences are Android libraries; Java Preferences is JVM-only; NSUserDefaults is iOS-only.
- `featured-compose/` and `featured-debug-ui/`: Compose adapters and the flag inspector, depending on core rather than concrete persistence providers.
- `featured-testing/`: test builders over core's in-memory provider.
- `featured-platform/`: `defaultLocalProvider` expect/actual factories. Android uses DataStore with a `Context`, iOS uses NSUserDefaults, and JVM uses Java Preferences.
- `featured-gradle-plugin/`: a separate included build, not a normal root subproject. Find DSL/resolution, generators/tasks, `manifest/`, and `aggregation/` under its main Kotlin package. Its tests live alongside the plugin, not under core.
- `featured-lint-rules/` and `featured-detekt-rules/`: published static-analysis integrations. Register checks through Lint's issue registry or Detekt's rule-set provider; preserve their JAR manifest/service registration.
- `featured-shrinker-tests/`: standalone JVM tests of release dead-code elimination.
- `featured-bom/`: published version-alignment constraints; include new published library modules here.
- `sample/feature-*/`: feature-owned DSL declarations, public observer bridges, and ViewModels. `sample/shared/` aggregates manifests and hosts shared Compose UI. `sample/android-app/` and `sample/desktop/` are platform shells.
- `iosApp/`: Swift consumer of the sample framework, with SwiftUI/Combine bridge code.
- `gradle/`: version catalog and wrapper. `docs/specs/` and `docs/cc-verification/`: plugin design and verification references.

Read root and module-local `CLAUDE.md` files for additional context and `CONTRIBUTING.md` for contribution/release policy. Resolve discrepancies in task names and API examples against build scripts and source.

For code navigation, follow `.claude/rules/ast-index.md`: use `ast-index` before text search, prefer symbol/usage/outline queries, and update the index after source or branch changes. Scope text searches to the relevant module/source set and exclude generated build output.

## Architecture notes

### Runtime and provider contracts

- Keep `ConfigParam<T>` a typed descriptor and `ConfigValue<T>` a value plus source. Treat flag keys as shared storage/cache identities; keep them unique across aggregated modules and consistent in type.
- Use `ConfigValues` to compose providers. The default precedence is **local override, then remote, then declared default**. `ValueResolutionStrategy` can replace selection policy after both providers have been read.
- Return `null` from a provider read when no stored value exists. Let `ConfigValues` perform fallback; do not turn an absent local value into a stored override.
- Preserve the read-path error boundary: provider read failures are reported through `onProviderError` and fall back; coroutine cancellation propagates. Explicit fetch/initialization failures and custom resolution-strategy exceptions propagate to the caller.
- Keep local `observe` flows alive until cancellation. Emit the current local value or DEFAULT immediately, then signal writes/removals; storage/converter failures must not permanently terminate observation.
- Preserve `ConfigValues.observe`'s trigger model: local emissions trigger a full resolution, rather than becoming the effective value directly. Deduplicate using the entire `ConfigValue`, including source.
- Preserve the atomic snapshot/warm-set updates across KMP targets when changing caching or concurrency.

### Feature ownership and aggregation

- Declare application flags in each owning module's `featured { localFlags { ... }; remoteFlags { ... } }` DSL.
- Keep generated objects/accessors `internal` by default. Expose deliberate public `ConfigValues` observer/setter bridges from feature modules instead of reaching into another module's generated declarations.
- Construct a `ConfigValues` per feature and share provider instances across them. Give a cross-feature debug surface its own container over the same providers and `GeneratedFeaturedRegistry.all`.
- Apply `dev.androidbroadcast.featured` to flag-producing modules; apply `dev.androidbroadcast.featured.application` to the aggregator. Add producers through `featuredAggregation(project(...))`.
- Keep aggregation declarative through manifest artifacts and schema attributes. Do not inspect or mutate other projects from plugin configuration.
- The producer and aggregator plugins add generated Kotlin to compilation themselves (KMP `commonMain`, Kotlin/JVM `main`, or the AGP `main` source set). Do not add a manual `srcDir` for `generateConfigParam` or `generateFeaturedRegistry`. The registry is written to `build/generated/featured/registry`, separate from `generateConfigParam`'s `build/generated/featured/commonMain`.

- Add a normal `implementation`/`api` dependency as well as `featuredAggregation` when a manifest references an enum defined in the producer. The manifest dependency does not expose that enum on the compile classpath.
- Keep plugin task state provider-backed and serializable. Snapshot project metadata during configuration; avoid execution-time `Project` access. Keep DSL descriptors lazy so default/package/visibility changes invalidate generated outputs and caches.

### Release optimization

- Keep generated synchronous accessors synchronous: they use `getValueCached`, and R8 rules target their exact JVM signatures. Local Boolean accessors return `Boolean`; other local accessors return the raw type; remote accessors return `ConfigValue<T>`.
- Treat local defaults as release-build constants when generated R8 rules apply. Use remote flags for behavior that must remain runtime-controlled in release builds.
- Keep generator output names and R8 targets aligned. Extension-file JVM names derive from the module path; changing generated object names does not rename extension files.
- Preserve AGP-specific consumer-rule wiring for both ordinary and KMP Android libraries. Library rules must reach the consuming app's R8 through the AAR, and explicit generator task dependencies are required in addition to variant file providers.
- Keep Swift `DISABLE_<FLAG>` compilation conditions and generated iOS constants aligned with their consumers. Include generated xcconfig in the appropriate Release configuration; merely generating a file does not configure Xcode.

## Code style and build conventions

- Keep public library/plugin declarations explicit in visibility and exposed types; explicit API mode is enabled for library modules, including compiler flags in Android-only providers.
- Write code comments and KDoc in English. Let root Spotless/ktlint format Kotlin and `.kts`; use `.swiftlint.yml` for the Swift sample.
- Use `gradle/libs.versions.toml` for shared dependency versions and aliases. Keep the Android Lint artifact aligned with AGP using the relationship documented in the catalog.
- Keep release versioning in root `gradle.properties` under `VERSION_NAME`; the included plugin build reads it from there.
- Follow the AGP built-in Kotlin/KMP Android plugin setup already present. Do not add a separate Kotlin Android plugin to modules using built-in Kotlin.

## Common workflows

### Add or change a sample feature flag

1. Edit the owning `sample/feature-*/build.gradle.kts` DSL and keep storage keys globally unique.
2. Add/update its public observer/setter bridge and feature ViewModel; consume those from shared UI.
3. For a new feature module, include it in root settings, add aggregator and normal code dependencies to `sample/shared`, and export it from the sample Apple framework when Swift needs its public types.
4. Reuse the shell's shared provider; register enum converters before any reads/writes.
5. Run code generation through the consuming build, test the affected behavior, and validate release shrinking if changing a local flag's release behavior.

Use these sample entry points:

```bash
./gradlew :sample:android-app:installDebug
./gradlew :sample:desktop:run
./gradlew :sample:android-app:assembleRelease
```

Build the Swift sample with the Xcode command above; its build phase invokes `:sample:shared:embedAndSignAppleFrameworkForXcode`. The sample framework is `FeaturedSampleApp`; the distributable core framework is `FeaturedCore`.

### Inspect or change code generation

- Run `./gradlew resolveFeatureFlags` to resolve DSL flags across matching modules; use module-qualified invocations for focused work.
- Inspect outputs under the producer's `build/featured/` and `build/generated/featured/`; change the DSL or generators, not generated Kotlin.
- Use `generateConfigParam`, `generateFeaturedProguardRules`, `generateIosConstVal`, `generateXcconfig`, and `generateFeaturedManifest` on producers, and `generateFeaturedRegistry` on the aggregator.
- Use `generation { }` for package/visibility defaults and per-section overrides. Set custom `className` only in `localFlags`/`remoteFlags`, not in the module-wide generation block. Keep custom package/class pairs unique across modules.
- Keep expiry verification date-sensitive: `verifyExpiredFlags` runs before code/optimization generators, warns by default, and supports `ExpiredFlagsMode.ERROR`. Do not cache away date-based checks.

For local artifact testing, root `./gradlew publishToMavenLocal` also forwards to the included plugin build. For distribution, use `:core:zipXCFramework` and the release process in `CONTRIBUTING.md`; `Package.swift` is a binary distribution manifest whose URL/checksum are managed by publishing CI.

## Known pitfalls

- Warm each container's synchronous snapshot before depending on generated getters: call `initialize()` for cached remote state, then `warmUp(params)`. A cold `getValueCached` returns the declared default without provider I/O; fetch/initialize automatically refresh only registered warm parameters.
- Write through `ConfigValues.override` when the caller's synchronous cache must update immediately. Direct provider writes reach active observers but do not synchronously update every container's snapshot.
- Do not assume `clearOverrides()` clears cached values. It clears provider storage; use `resetOverride(param)` for each known parameter when synchronous reads must immediately reflect removal, including fallback to DEFAULT.
- On Android, call `defaultLocalProvider(context)` for persistent DataStore storage. The no-argument Android overload is deprecated and returns in-memory storage.
- Register `enumConverter<E>()` with persistent local providers before using enum flags. Generating an enum descriptor alone does not configure serialization.
- Avoid broad `-keep`/`@Keep` rules on code expected to disappear: they can retain unreachable classes despite correctly folded flag branches.
- Keep nested checkouts/worktrees outside the checkout used for formatting. Root Spotless targets all `**/*.kt` and `**/*.kts`, excludes build directories, and can scan ignored/untracked nested repositories.
- Kotlin/Native targets are `iosArm64` (device) and `iosSimulatorArm64` (Apple Silicon simulator). Do not add `iosX64`; the published XCFramework has no Intel-simulator slice.
- Preserve the out-of-process Kotlin/Native compiler configuration in `gradle.properties`; concurrent framework links otherwise share the Gradle daemon's heap.
- Treat `iosApp/Configuration/FeatureFlags.generated.xcconfig` as a checked-in placeholder, not live generated output. Generate from the actual producer module; its embedded example paths are not the sample's Gradle paths.

## PR and Git instructions

- Branch from and target `develop` for feature, fix, and documentation PRs; keep each PR focused. Use squash merges into `develop`.
- Use the checklist in `.github/pull_request_template.md`: tests, documentation/KDoc, changelog, and Spotless. Review public API changes explicitly; compatibility is reviewed manually.
- Follow `CONTRIBUTING.md` for versioning: stable Android breaking changes require a major version; preview iOS/JVM APIs may change in minor releases. Do not invent a deprecation/migration window.
- Keep release branch operations distinct: release `develop` into `main` with a real merge, not a squash; synchronize back through the documented squash-PR procedure.

## Maintain this document

Keep `AGENTS.md` accurate whenever project changes affect these instructions. Correct stale commands and contracts, and add durable repository-specific guidance as it becomes verifiable. Keep it concise and under roughly 400 lines; omit temporary status, timestamps, feature inventories, machine-specific details, secrets, generic advice, and one-time setup instructions.
