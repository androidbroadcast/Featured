---
type: spec
slug: api-compat-gate
date: 2026-11-07
status: draft
platform: [android]
surfaces: [library]
risk_areas: [compatibility]
non_functional: [ci]
acceptance_criteria_ids: [AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7]
design:
---

# Spec: Android public API compatibility gate

Date: 2026-11-07
Status: draft
Slug: api-compat-gate

GitHub issue: https://github.com/androidbroadcast/Featured/issues/225

## Context and Motivation

Android is a stable Featured platform. A breaking public-API change on that surface requires a major version. Binary Compatibility Validator was removed in #150, and the public Android surface is reviewed by hand only. `CLAUDE.md` and `CONTRIBUTING.md` currently say there is no automated gate.

iOS and JVM are preview. Their public API may change in a minor release without a major bump. This slice does not put those surfaces under a major-bump rule.

The promised outcome is a dump-and-check gate for the public Android surface. A public signature change in a gated module fails CI until the committed baseline is updated on purpose. Issue #225 can be closed from that check.

## Public contract

The gate covers the public JVM binary signatures of the Android compilation that is published for each module in the table below. That compilation is what an Android consumer links against: `commonMain` plus `androidMain` for a Kotlin Multiplatform module, and the library main source set for an Android-only module.

A signature is in the contract when it is part of the public binary ABI of that compilation: public classes and public or protected members, as JVM descriptors. `internal` and private declarations are not in the contract. KDoc, source formatting, and behavior that does not change those signatures are not in the contract.

`commonMain` declarations compiled into the Android artifact are Android surface. A change there fails this gate even though preview targets also compile that source. That is the existing Android stability rule, not a new rule for iOS or JVM.

These are not in this contract:

- `jvmMain` and other JVM-only source sets
- `iosMain` and other Apple targets, including the SKIE Swift overlay
- generated flag code in a consumer project
- the Gradle plugin DSL

The gate fails on any difference in the committed Android dump, including an added signature. It does not auto-accept. Updating the baseline is a reviewed diff in the same change. CI must not rewrite the baseline to turn a failure into a pass.

The gate does not compute the version bump. Existing versioning stays:

- A breaking Android signature change (removed or renamed public symbol, or a signature change that requires a call-site update) still requires a major bump.
- An additive Android public signature still requires a minor bump, and still requires a baseline update.
- A signature change that does not appear in a gated Android dump may still ship in a minor release. It does not require a major bump and does not fail this gate.
- Featured still has no deprecation or migration window. This gate does not add one.

A behavioral break that leaves signatures unchanged is still a breaking Android change under the existing policy. This gate does not detect it and does not replace that review.

## Modules in the gate

These published modules ship an artifact Android application or Android test code links against. They are the whole gate.

| Gradle project | Published artifact | Android compilation |
|---|---|---|
| `:core` | `dev.androidbroadcast.featured:featured-core` | KMP `android` (`commonMain` + `androidMain`) |
| `:featured-compose` | `dev.androidbroadcast.featured:featured-compose` | KMP `android` |
| `:featured-debug-ui` | `dev.androidbroadcast.featured:featured-debug-ui` | KMP `android` |
| `:featured-platform` | `dev.androidbroadcast.featured:featured-platform` | KMP `android` |
| `:featured-testing` | `dev.androidbroadcast.featured:featured-testing` | KMP `android` |
| `:providers:configcat` | `dev.androidbroadcast.featured:featured-configcat-provider` | KMP `android` |
| `:providers:datastore` | `dev.androidbroadcast.featured:featured-datastore-provider` | KMP `android` |
| `:providers:firebase` | `dev.androidbroadcast.featured:featured-firebase-provider` | Android library main source set |
| `:providers:sharedpreferences` | `dev.androidbroadcast.featured:featured-sharedpreferences-provider` | Android library main source set |

`:featured-testing` is in the gate because Android tests link its published API. `:featured-debug-ui` is in the gate because Android debug builds link it. Both are stable Android surface, not sample code.

Committed baseline, one file per gated module. CI compares that file and no other dump:

| Gradle project | Baseline |
|---|---|
| `:core` | `core/api/android/featured-core.api` |
| `:featured-compose` | `featured-compose/api/android/featured-compose.api` |
| `:featured-debug-ui` | `featured-debug-ui/api/android/featured-debug-ui.api` |
| `:featured-platform` | `featured-platform/api/android/featured-platform.api` |
| `:featured-testing` | `featured-testing/api/android/featured-testing.api` |
| `:providers:configcat` | `providers/configcat/api/android/featured-configcat-provider.api` |
| `:providers:datastore` | `providers/datastore/api/android/featured-datastore-provider.api` |
| `:providers:firebase` | `providers/firebase/api/android/featured-firebase-provider.api` |
| `:providers:sharedpreferences` | `providers/sharedpreferences/api/android/featured-sharedpreferences-provider.api` |

Each baseline is a text dump of the current public JVM signatures of that module's Android compilation, generated by the dumper the check uses, and committed. A missing, empty, or hand-written baseline does not satisfy the gate. `:core` has a public API, so `core/api/android/featured-core.api` is non-empty.

No other module has a baseline that this gate fails on.

## Out of the gate

These stay out. Do not add their signatures to the failing check, and do not document a major bump for a change that is only in this list.

| Module or surface | Why it is out |
|---|---|
| `:providers:javaprefs` (`featured-javaprefs-provider`) | JVM-only preview |
| `:providers:nsuserdefaults` (`featured-nsuserdefaults-provider`) | iOS-only preview |
| `jvm` target of a gated KMP module | JVM preview. A `jvmMain`-only signature is not Android surface |
| Apple targets of a gated KMP module, and SKIE output | iOS preview |
| `:featured-gradle-plugin` | Gradle plugin bytecode, not the Android library surface. This slice does not add a plugin ABI gate and does not invent a major-bump rule for the DSL |
| `:featured-lint-rules` | Android Lint classpath. Application code does not link it |
| `:featured-detekt-rules` | JVM Detekt classpath, not Android surface |
| `:featured-bom` | Version constraints, no signatures |
| `:sample:*`, `:featured-shrinker-tests`, `:featured-swift-dce-tests` | Not published |

`featured-registry` had a dump before #150. It is not a module on `develop`. Do not recreate it for this gate.

## Compatibility

Android remains the only platform under the major-bump rule. iOS and JVM remain preview: a public-API change there may ship in a minor release, with no migration window.

A `commonMain` signature change that changes a gated Android dump is an Android change. If it is breaking for Android callers, the version bump is major because Android is stable. Preview targets do not get their own gate, and they are not the reason for that bump.

The gate does not freeze preview surfaces. A later slice may add a non-blocking dump for them. This slice must not fail CI on that dump and must not describe it as a major-bump requirement.

## Check

The check is a dump-and-check of the nine baseline files above.

1. Build the public JVM signatures of each gated module's Android compilation.
2. Compare that dump to the committed baseline.
3. Fail if any baseline is missing, empty, or different.
4. Pass only when every gated Android dump matches its baseline.

The check runs in the existing CI workflow on pull requests to `develop` and `main`. It runs on a Linux runner. It does not compile Apple targets and does not read an iOS or JVM dump.

A public signature change in a gated module fails that CI job until the matching baseline file is updated in the same change. That file diff is the "on purpose" signal. Issue #225 can be closed from this job.

### What is not an acceptable check

Stock `apiCheck` from `org.jetbrains.kotlinx.binary-compatibility-validator` 0.18.x is not this gate. That plugin does not register API tasks for an Android target created by `com.android.kotlin.multiplatform.library` (Kotlin/binary-compatibility-validator#315). A green `apiCheck` can mean the Android compilation was skipped. Seven of the nine gated modules use that plugin.

Unmodified `checkKotlinAbi` on a KMP module is not this gate. Kotlin Gradle plugin 2.4.20, the version on `develop`, does see the Android-KMP compilation (KT-85950), but the same task also dumps the `jvm` target and klib targets, including iOS. `checkKotlinAbi` fails when any of those reference dumps is missing or different. Wiring that task into CI as-is would put preview iOS and JVM under this gate.

`binariesSource = MAVEN_PUBLICATIONS` is not required. Kotlin 2.4.20 reports that Android publications are not supported as an ABI input. The dump input is the Android compilation output, which is the bytecode Android consumers link.

The dumper may be Kotlin Gradle plugin ABI validation of the Android compilation, or another dumper of the same public JVM signatures. The CI failure condition is a difference in the nine Android baseline files. JVM and klib dumps, if a tool writes them, are not baselines and must not fail CI.

Do not take a third-party bridge plugin as a requirement of this spec.

`:providers:firebase` and `:providers:sharedpreferences` apply `com.android.library` and compile Kotlin with AGP's built-in Kotlin. Their check must read that main compilation. Skipping them because they do not apply `kotlin-android` explicitly does not satisfy the gate.

## Acceptance criteria

- [ ] **AC-1.** The failing check covers exactly the nine modules and nine baseline paths in "Modules in the gate". No other module fails this check.
- [ ] **AC-2.** Each baseline is a generated dump of that module's current Android public JVM signatures and is committed. `core/api/android/featured-core.api` is non-empty. A missing or empty baseline fails the check.
- [ ] **AC-3.** CI on pull requests to `develop` and `main` runs the check on a Linux runner. The check does not compile Apple targets.
- [ ] **AC-4.** On an unmodified tree the check passes. Adding a public function to `:core` `commonMain` without updating `core/api/android/featured-core.api` makes the check fail. Updating that baseline, or restoring the source, makes it pass. The temporary source change is not left in the tree.
- [ ] **AC-5.** Adding a public function only in `:providers:javaprefs`, or only in `:core` `jvmMain`, does not fail the check and does not require a baseline update. The check has no iOS dump input.
- [ ] **AC-6.** `CONTRIBUTING.md` and `CLAUDE.md` no longer say that public-API review is manual only. They name this Android gate, point at this spec for the module list, and keep the existing preview exception: iOS and JVM public API may change in a minor release without a major bump. They do not claim the gate computes the version bump.
- [ ] **AC-7.** CI does not update baselines. The only way to accept an Android signature change is a committed baseline diff.

## Decisions

- The gate is the public Android surface named above, not every module that had a dump before #150. That older set included JVM dumps, Detekt, Lint, and `featured-registry`.
- Preview iOS and JVM are not given a major-bump rule.
- `commonMain` is inside the Android dump because Android consumers link it. That does not create a preview-platform gate.
- The Gradle plugin, Lint rules, and Detekt rules are out of this slice.
- The "on purpose" signal is a reviewed baseline diff, not a green task that rewrites the dump.

## Out of scope

- Implementing the gate in this spec change.
- Automating the major or minor version bump.
- A dump or CI failure for iOS, JVM, the Gradle plugin, Lint, or Detekt.
- Detecting behavioral breaks that do not change signatures.
- Requiring a deprecation window.
