# Spec: Build-type flag defaults

Date: 2026-11-07
Status: draft
Issue: https://github.com/androidbroadcast/Featured/issues/297

This slice lets a local flag keep one base default and optionally set a different default for an Android build type. It does not add product-flavor defaults. It does not add a KMP build profile. Same-name local/remote isolation has landed on `develop` (#311). This slice does not change `FlagScope` or storage identity.

## Promised behavior

A local flag still has one base default: the `default` argument it has today. An optional build-type default may replace that base for one named Android build type. A build type with no build-type default uses the base default.

The effective default of build type `B` is the build-type default for `B` when one was declared, otherwise the base default. `debug` and `release` may differ. A custom build type such as `benchmark` follows the same rule. Two product flavors that share a build type share that build type's effective default. A flavor name is not a selector.

`defaultFor` changes the declared default used when no stored value exists. It does not call `ConfigValues.override`, and it does not write or clear a stored local value. An existing stored value still wins over the effective default, on every build type, under the resolution rules already in place.

The Android compilation of a build type observes that build type's effective default as the generated `ConfigParam.defaultValue`. A debug compilation and a release compilation of the same flag can therefore hold different defaults. One shared constant cannot be both. Today's single `generateConfigParam` output, wired into `commonMain` or the AGP `main` source set, is shared across Android variants. The implementation must still make the debug compilation and the release compilation observe their own effective defaults. Accessor JVM signatures do not change.

Non-Android compilations have no Android build type. JVM, iOS, and any shared compilation that is not an Android variant use the base default. iOS `DISABLE_<FLAG>` conditions and generated iOS constants follow the base default, not the release effective default. This slice does not generate per-configuration xcconfig files.

R8 `-assumevalues` and `-checkdiscard` use the release build type's effective default: the release build-type default if one exists, otherwise the base default. A debug build-type default must not be the value assumed by the release rule, and it must not be the reason a `-checkdiscard` entry is present in the release rule.

The generated rule files stay the release rules (`proguard-featured.pro` and `proguard-featured-checkdiscard.pro`). This slice does not emit a separate rule file per build type or per flavor. A minified variant other than `release` that consumes those same files therefore assumes the release effective default, not its own build-type default. That is the rule for this slice.

## Public contract

The call shape is the issue's override-on-top-of-base form. The existing `default` parameter stays the base default. Existing declarations that do not call `defaultFor` keep one default for every build type.

```kotlin
featured {
    localFlags {
        boolean("new_checkout", default = false) {
            defaultFor("debug", true)
        }
        int("max_retries", default = 3) {
            defaultFor("debug", 1)
            defaultFor("release", 0)
        }
        boolean("experiment", default = true) {
            defaultFor("benchmark", false)
        }
    }
}
```

`defaultFor` is a public method on `FlagSpec`, the receiver of the existing configure block. Typed overloads:

```kotlin
public fun defaultFor(buildType: String, value: Boolean)
public fun defaultFor(buildType: String, value: Int)
public fun defaultFor(buildType: String, value: Long)
public fun defaultFor(buildType: String, value: Float)
public fun defaultFor(buildType: String, value: Double)
public fun defaultFor(buildType: String, value: String)
```

The `String` overload is the build-type default for a `string` flag and for an enum flag. An enum value is the constant name, the same form as `enum(..., default = "LEGACY")`. The enclosing quotes that `string(...)` stores today are not part of the caller's value.

The build fails, and names the flag, when any of these are true:

- `defaultFor` is used inside `remoteFlags`. A remote flag keeps the single default it has today. Remote values stay runtime-resolved and are not pinned by R8.
- The override value's type does not match the flag. A boolean flag does not accept an `Int`. A string flag does not accept a `Boolean`. An enum flag does not accept a non-string. The check is against the flag's declared type, not against which overload compiled.
- The build type name is blank after trimming. The stored name is the trimmed name. Matching is case-sensitive: `Debug` is not `debug`.
- The same flag declares two build-type defaults for the same trimmed build type name.
- The project has no Android build types. There is then no build type the call can apply to.
- The trimmed name is not an Android build type of that project, once those names are known. A flavor name, a variant name such as `freeDebug`, and a typo all fail. The check runs after the project's build types exist, so declaring `benchmark` and calling `defaultFor("benchmark", ...)` is valid in either order in the same build script.
- An enum build-type default is blank after trimming. This slice does not resolve the enum class. A missing constant is the same failure the base `default` already has: it is not a new check.

A build-type default equal to the base default is valid. It does not change the effective default.

`discard(...)` stays limited to a local boolean flag, and it stays a release check. Validity follows the release effective default, not the base default and not the debug build-type default. The order of `discard` and `defaultFor` inside the configure block does not matter. The decision is made from the finished flag.

- Release effective default `false`: `discard(...)` is valid, including when the base default is `true` and `defaultFor("release", false)` makes it false, and including when `defaultFor("debug", true)` is also present.
- Release effective default `true`: `discard(...)` fails the build, including when the base default is `false` and `defaultFor("release", true)` makes it true, and including when the only `false` is `defaultFor("debug", false)`.
- A debug build-type default of `false` does not by itself emit a `-checkdiscard` entry, and it does not by itself satisfy `discard(...)`.

The release `-assumevalues` literal for a supported local flag is the release effective default, formatted the way a base default of that same value is formatted today. Enum flags stay out of `-assumevalues`. A release build-type default on an enum changes the generated release `ConfigParam.defaultValue` only.

Generated accessor JVM signatures do not change. A local Boolean accessor still returns `Boolean`. Other local accessors still return the raw type. Remote accessors still return `ConfigValue<T>`. `FlagScope.LOCAL` and `FlagScope.REMOTE` stay as isolation defined them. The public `key` does not change.

### Manifest and debug UI

`featured-manifest.json` `defaultValue` remains the base default. Build-type defaults are not a manifest field. `SCHEMA_VERSION` stays 1. `GeneratedFeaturedRegistry.all` is generated from that manifest, so its `ConfigParam.defaultValue` is the base default in every compilation that reads the registry.

`FeatureFlagsDebugScreen`, when given the registry, shows that base default. It does not gain a build-type selector in this slice. A caller that reads the feature module's generated `ConfigParam` from an Android variant sees that variant's effective default. Those two sources can differ when a build-type default exists. That split is part of this slice, not an accident.

## Compatibility

This is an additive Gradle DSL. A declaration that does not call `defaultFor` compiles and behaves as it does today: one default, and that default is the value R8 assumes and the value iOS strips on. No public Kotlin symbol in the Android library artifacts is added or removed. The Gradle plugin DSL is not part of the Android binary-compatibility gate. There is no migration of stored flag values.

A caller who sets `default = false` and `defaultFor("debug", true)` keeps Android release and iOS on `false`, and turns the flag on by default only in the Android `debug` build type. That is the intended way to express "on in debug, off in shipping builds" in this slice.

A caller who sets `default = true` and `defaultFor("release", false)` gets Android release R8 of `false` and an iOS build that still uses `true`. The changelog states that split so it is not a silent change.

Already compiled consumers of the library are unchanged. Projects that do not apply the plugin are unchanged.

### Docs that currently say the single default is the release constant

These sentences become false when a release build-type default differs from the base. The implementation updates them. It does not leave the contradiction in the repository.

- `CLAUDE.md` release-time dead-code elimination: flags are stripped from Android release when the release effective default is the disabling value, not merely when the base `default` is `false`.
- `README.md` dead-code elimination bullet: same rule.
- `featured-gradle-plugin/CLAUDE.md`: show `defaultFor` on a local flag, and state that `discard(...)` follows the release effective default.
- `AGENTS.md` release optimization: "Treat local defaults as release-build constants when generated R8 rules apply" means the release effective default. A debug build-type default is not that constant. iOS `DISABLE_<FLAG>` stays aligned with the base default in this slice.

`CHANGELOG.md` under `[Unreleased]` states all of the following:

- A local flag keeps its `default` as the base default.
- `defaultFor(buildType, value)` sets a different default for that Android build type.
- A build type with no `defaultFor` uses the base default.
- R8 `-assumevalues` and `-checkdiscard` use the release build type's effective default.
- A debug build-type default is not the release rule.
- Product flavors are not supported.
- iOS `DISABLE_` conditions, generated iOS constants, and non-Android compilations use the base default.
- `defaultFor` does not write a stored override. A stored local value still wins.

## Checks

The feature is shown when all of the following are true.

1. A DSL test declares a local flag with base `false`, `defaultFor("debug", true)`, and no release build-type default. The debug effective default is `true`. The release effective default is `false`. A build type that has no `defaultFor`, including a declared custom build type such as `benchmark`, uses `false`.
2. A DSL test declares a local flag with base `true`, `defaultFor("debug", true)`, and `defaultFor("release", false)`. The debug effective default is `true`. The release effective default is `false`. The two differ.
3. The generated release rule file `build/featured/proguard-featured.pro`, produced for a flag whose debug build-type default and release effective default differ, assumes the release effective default. The debug value is not the `-assumevalues` return literal for that flag. Both shapes in checks 1 and 2 are covered: missing release `defaultFor` uses the base; a present release `defaultFor` uses that value and ignores the debug value.
4. `discard("com.example.Feature")` on a local boolean whose release effective default is `false` is accepted when the debug build-type default is `true`. The release `-checkdiscard` file contains that class specification. The same call is rejected when the release effective default is `true` and the debug build-type default is `false`. In that rejected case the class specification is not a release rule.
5. A remote flag that calls `defaultFor` fails the build. The failure names the flag. No remote `-assumevalues` line is generated for it.
6. Configuration fails for a blank build type, a duplicate build type on one flag, a name that is not an Android build type of the project, a flavor or variant name, a type mismatch, and `defaultFor` on a project with no Android build types.
7. A generator check shows the Android debug compilation's generated `ConfigParam.defaultValue` is the debug effective default, and the Android release compilation's generated `ConfigParam.defaultValue` is the release effective default, for a flag where those differ. The local accessor JVM signature is unchanged. `FlagScope` is unchanged.
8. The manifest `defaultValue` for that flag is the base default. `schemaVersion` remains 1. No build-type default field is added.
9. iOS xcconfig and iOS const-val generation for a flag with a release build-type default different from the base still use the base default.
10. `CHANGELOG.md` `[Unreleased]` contains the statements in the compatibility section. `CLAUDE.md`, `README.md`, `featured-gradle-plugin/CLAUDE.md`, and `AGENTS.md` no longer say that the base `default` alone is what the release R8 rule assumes.

A flag that does not call `defaultFor` still produces the same `-assumevalues` literal it produces today. Existing shrinker tests for that shape still apply.

## Out of scope

- Product flavors, flavor dimensions, and variant names as selectors. `freeDebug` is not a build type.
- A separate R8 rule file per non-release build type. Non-release minify variants that consume the release rule file assume the release effective default.
- iOS configurations, JVM profiles, and any non-Android build-type default. Shipping iOS follows the base default even when `defaultFor("release", ...)` differs.
- Remote-flag build-type defaults.
- Putting build-type defaults in the manifest, bumping `SCHEMA_VERSION`, or teaching `FeatureFlagsDebugScreen` to select a build-type default from the registry.
- Changing `FlagScope`, storage keys, accessor signatures, or resolution order.
- Resolving enum constant names at configuration time.
