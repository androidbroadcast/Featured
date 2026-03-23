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

## Code Sharing Between Detekt and Lint

Detekt and Android Lint use **different AST models** — Detekt works on raw
Kotlin PSI (`KtElement` hierarchy), while Lint works on UAST (`UElement`
hierarchy). Detection logic cannot be shared.

What can be shared:
- **Issue IDs** — `"HardcodedFlagValue"` must be identical in both so that
  `@Suppress("HardcodedFlagValue")` works for both tools. This is enforced by
  convention (same string literal), not by a shared constant module. Given the
  small number of rules, a dedicated shared module adds complexity without
  meaningful benefit.
- **Test fixture strings** — both test suites use inline Kotlin snippets as
  strings. These could theoretically live in a shared `testFixtures` source set,
  but again, the added module boundary is not worth it for a handful of strings.

**Decision:** no shared module. Duplication is intentional and minimal.

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

- Plugins: `alias(libs.plugins.kotlinJvm)`, `alias(libs.plugins.bcv)`,
  `alias(libs.plugins.mavenPublish)` — same set as `:featured-detekt-rules`
- Dependencies:
  - `compileOnly(libs.lint.api)` — `com.android.tools.lint:lint-api:32.1.0`
  - `testImplementation(libs.lint.tests)` — `com.android.tools.lint:lint-tests:32.1.0`
- Version catalog: add `lint` version alias `32.1.0`.
  Formula: lint_major = agp_major + 23, patch tracks AGP patch exactly
  (AGP 9.1.0 → lint 32.1.0; if AGP bumps to 9.2.0, lint must move to 32.2.0).
- `explicitApi()` + `jvmToolchain(21)` matching the rest of the project
- `mavenPublishing` block with `artifactId = "featured-lint-rules"`
- The `com.android.lint` Gradle plugin is **not required** — the JAR manifest
  entry `Lint-Registry-v2` is written manually in `build.gradle.kts` via a
  `tasks.jar { manifest { ... } }` block, which is the standard approach for
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

The ID matches the Detekt rule so `@Suppress("HardcodedFlagValue")` silences
both tools with one annotation.

### Detection logic

In Kotlin, `param.defaultValue` is a **property access**, not a function call.
UAST models it as a `USimpleNameReferenceExpression` with identifier
`"defaultValue"` inside a `UQualifiedReferenceExpression`, **not** as a
`UCallExpression`. Using `UCallExpression` would only catch explicit Java-style
`getDefaultValue()` calls, which no Kotlin caller would ever write.

Correct approach — `SourceCodeScanner` with:

```
getApplicableUastTypes() = listOf(USimpleNameReferenceExpression::class.java)

visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression)
  └─ node.identifier == "defaultValue"? → proceed
  └─ parent is UQualifiedReferenceExpression? → get receiver expression
  └─ val psiType = receiver.getExpressionType() as? PsiClassType ?: return
  └─ val psiClass = psiType.resolve() ?: return
  └─ evaluator.extendsClass(psiClass, "dev.androidbroadcast.featured.ConfigParam", false)
  └─ context.report(ISSUE, node, context.getLocation(node), message)
```

This handles both simple receivers (`param.defaultValue`) and chained receivers
(`flags.darkMode.defaultValue`) identically — in both cases the visitor fires
on the `defaultValue` name node, and the receiver is resolved via
`getExpressionType()` on the parent `UQualifiedReferenceExpression`'s receiver.

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
    // minApi must be lower than api to allow older AGP consumers to load the registry.
    // 10 is the stable minimum that supports the Vendor API.
    override val minApi = 10
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

Uses `lint-tests` (`LintDetectorTest` base class).

Every test provides a `ConfigParam` stub so the type resolver can resolve the
type. The stub must declare `T : Any` (non-nullable upper bound matching the
real class) and include `val defaultValue: T`. The real `ConfigParam` primary
constructor is `internal`, so the stub omits the full constructor and uses a
minimal form sufficient for type-checking:

```kotlin
private val configParamStub = kotlin("""
    package dev.androidbroadcast.featured
    class ConfigParam<T : Any>(val key: String, val defaultValue: T)
""").indented()
```

Tests that call `ConfigParam(key = "x", defaultValue = false)` must ensure
the stub constructor matches. If constructor shape diverges from the real API,
prefer testing via local variable declarations with explicit types:
```kotlin
val flag: ConfigParam<Boolean> = TODO()
flag.defaultValue
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
