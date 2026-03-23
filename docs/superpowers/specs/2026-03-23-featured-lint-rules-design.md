# Design Spec: `featured-lint-rules` — Android Lint Pilot

**Date:** 2026-03-23
**Status:** Approved
**Scope:** Pilot — `HardcodedFlagValue` detector only

---

## Background

The project already ships `featured-detekt-rules` with three rules:
`MissingFlagAnnotation`, `HardcodedFlagValue`, and `ExpiredFeatureFlag`.

All Detekt rules rely on **name-based heuristics** because Detekt runs without
full type resolution. The comment in `PsiExtensions.kt` calls this out explicitly:

> "Detection is heuristic (name-based) because Detekt rules run without full
> type resolution in the default lint mode."

Android Lint runs UAST with the full compiled classpath, enabling exact type
checks via `JavaEvaluator`. The goal is a parallel `featured-lint-rules` module
that provides the same rules with zero false positives.

The pilot implements `HardcodedFlagValue` — the rule most affected by Detekt's
heuristic limitation (any `x.defaultValue` on any simple name fires).

---

## Module Structure

New Gradle module `:featured-lint-rules`, mirroring `:featured-detekt-rules`:

```
featured-lint-rules/
├── build.gradle.kts
└── src/
    ├── main/kotlin/dev/androidbroadcast/featured/lint/
    │   ├── HardcodedFlagValueDetector.kt
    │   └── FeaturedIssueRegistry.kt
    └── test/kotlin/dev/androidbroadcast/featured/lint/
        └── HardcodedFlagValueDetectorTest.kt
```

`settings.gradle.kts` gains `include(":featured-lint-rules")`.

### Build configuration

- Plugin: `alias(libs.plugins.kotlinJvm)`
- Dependencies:
  - `compileOnly(libs.lint.api)` — `com.android.tools.lint:lint-api:32.1.0`
  - `testImplementation(libs.lint.tests)` — `com.android.tools.lint:lint-tests:32.1.0`
- Version catalog: add `lint` version alias `32.1.0` (AGP 9.1.0 → lint = agp_major + 23 = 32)
- `explicitApi()` + `jvmToolchain(21)` matching the rest of the project
- `mavenPublishing` block with `artifactId = "featured-lint-rules"`
- The `com.android.lint` Gradle plugin is **not required** — the JAR manifest
  entry `Lint-Registry-v2` is written manually in `build.gradle.kts` via a
  `jar { manifest { ... } }` block, which is the standard approach for
  non-Android modules.

---

## Detector Design: `HardcodedFlagValueDetector`

### Issue definition

| Field | Value |
|---|---|
| ID | `HardcodedFlagValue` |
| Category | `Correctness` |
| Severity | `WARNING` |
| Priority | 6 / 10 |
| Brief description | Accessing `ConfigParam.defaultValue` directly bypasses providers |
| Explanation | Accessing `defaultValue` directly bypasses any local or remote provider overrides, making the flag effectively hardcoded. Use `ConfigValues` to read the live value. |

The ID matches the Detekt rule so `@Suppress("HardcodedFlagValue")` works for
both tools.

### Detection logic

Scan: `SourceCodeScanner`, override `getApplicableUastTypes()` returning
`listOf(UCallExpression::class.java)`.

```
visitCallExpression(node: UCallExpression)
  └─ node.kind == UastCallKind.NESTED_ARRAY_ACCESS? → skip
  └─ node is UQualifiedReferenceExpression selector named "defaultValue"?
       (check via node.methodName or traverse to parent UQualifiedReferenceExpression)
  └─ resolve receiver PsiType
  └─ evaluator.extendsClass(receiverType, "dev.androidbroadcast.featured.ConfigParam", false)
  └─ context.report(ISSUE, node, location, message)
```

Alternatively, override `visitSimpleNameReferenceExpression` on the property
access — whichever UAST visitor cleanly catches Kotlin property reads (which
compile to `getDefaultValue()` calls but appear as field reads in UAST).

### Message

```
Accessing 'defaultValue' directly on a ConfigParam bypasses provider overrides.
Use ConfigValues to read the live value instead.
```

### `FeaturedIssueRegistry`

```kotlin
class FeaturedIssueRegistry : IssueRegistry() {
    override val issues = listOf(HardcodedFlagValueDetector.ISSUE)
    override val api = CURRENT_API
    override val minApi = CURRENT_API
    override val vendor = Vendor(
        vendorName = "Featured",
        feedbackUrl = "https://github.com/AndroidBroadcast/Featured/issues",
    )
}
```

JAR manifest entry (in `build.gradle.kts`):
```kotlin
tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "dev.androidbroadcast.featured.lint.FeaturedIssueRegistry")
    }
}
```

---

## Test Design

Uses `lint-tests` (`LintDetectorTest` base class or `TestLintTask`).

Every test provides a `ConfigParam` stub so the type resolver works:

```kotlin
private val configParamStub = kotlin("""
    package dev.androidbroadcast.featured
    class ConfigParam<T>(val key: String, val defaultValue: T)
""").indented()
```

### Test cases

| # | Scenario | Expected |
|---|---|---|
| 1 | `someParam.defaultValue` where `someParam: ConfigParam<Boolean>` | 1 warning |
| 2 | `someParam.defaultValue` where `someParam: String` | 0 warnings |
| 3 | `configValues[flag]` (correct usage via `ConfigValues`) | 0 warnings |
| 4 | Chained: `flags.darkMode.defaultValue` where `darkMode: ConfigParam<Boolean>` | 1 warning |
| 5 | `defaultValue` on a `ConfigParam<T>` method parameter | 1 warning |
| 6 | `defaultValue` accessed on a local variable of unrelated type | 0 warnings |

---

## Out of Scope (this spec)

- Porting `MissingFlagAnnotation` and `ExpiredFeatureFlag` to Lint
- QuickFix / `LintFix` suggestions
- Baseline file integration

These will be addressed in follow-up specs once the pilot module is validated.
