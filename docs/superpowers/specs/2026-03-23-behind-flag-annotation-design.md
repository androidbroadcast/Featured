# Design: `@BehindFlag` / `@AssumesFlag` — Feature Flag Guard Annotations

**Date:** 2026-03-23
**Status:** Draft
**Modules affected:** `core`, `featured-detekt-rules`

---

## Problem

There is no static mechanism to enforce that code intended to run only when a feature flag is
enabled is actually guarded by a flag check at every call site. A developer can introduce a new
screen, class, or function behind a flag on their branch, but nothing prevents a colleague from
calling that code without checking the flag first — the mistake is invisible until runtime.

In a KMP project, call sites and annotated declarations are routinely in different files and
modules. A same-file-only rule would cover fewer than 10% of real violations.

---

## Goals

1. Provide a way to annotate code that must only be used when a specific feature flag is active.
2. Provide a way to declare that a calling context already guarantees the flag is checked.
3. Catch violations at lint time (Detekt with type resolution), cross-file and cross-module.
4. Validate that flag name strings refer to real declared flags, catching typos at lint time.
5. Zero runtime overhead — SOURCE retention annotations only.

---

## Non-Goals

- Transitive call-chain analysis across arbitrary depth (only direct syntactic context).
- Tracking method calls on an already-constructed instance of an annotated class.
- Lambda / functional type analysis.
- Secondary constructor guarding (primary constructor only).
- Runtime enforcement.
- Detecting renamed boolean helpers (`if (isNewCheckoutEnabled())`) — only direct
  `KtNameReferenceExpression` matching `flagName` in conditions is matched.

---

## Annotations (`core` module)

### `@BehindFlag`

Marks a function, class, or property that must only be used inside a valid feature-flag context.

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class BehindFlag(
    /**
     * The name of the Kotlin property (declared with `@LocalFlag` or `@RemoteFlag`)
     * that guards this declaration. Must match the exact property name, e.g. `"newCheckout"`.
     *
     * Validated by the [InvalidFlagReference] Detekt rule.
     *
     * See also: [AssumesFlag]
     */
    val flagName: String,
)
```

**KDoc model:** Follow the style of `@ExpiresAt` — single-paragraph description, intended
workflow as a numbered list, a usage code block, `See also` cross-reference to `@AssumesFlag`.

**Coverage note:** Annotation classes with no body produce no coverable lines and do not
affect the `core` module's ≥90% koverVerify requirement.

---

### `@AssumesFlag`

Marks a function or class that takes explicit responsibility for ensuring the named flag is
checked before execution. Call sites of `@BehindFlag("X")` code inside an `@AssumesFlag("X")`
scope are not warned by `UncheckedFlagAccess`.

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class AssumesFlag(
    /**
     * The name of the feature flag property this scope guarantees is checked before execution.
     * Must match the `flagName` of the corresponding `@BehindFlag` declaration.
     *
     * **Escape hatch.** No automated verification that the flag is actually checked inside
     * the scope. The developer asserts correctness. Misuse silently bypasses
     * [UncheckedFlagAccess].
     *
     * See also: [BehindFlag]
     */
    val flagName: String,
)
```

**Scope of `@AssumesFlag` on CLASS:** covers member functions and `init` blocks.
Companion object members are excluded (separate `KtObjectDeclaration` in PSI).

---

## Detekt Rules (`featured-detekt-rules` module)

### `UncheckedFlagAccess` — requires type resolution

**Severity:** Warning
**Debt:** `Debt.TWENTY_MINS`

Fires when a call to `@BehindFlag("X")`-annotated code is found outside a valid context.

#### Why type resolution is required

The annotated declaration and its call site are typically in different files or modules in a
KMP project. PSI-only analysis can only match by name heuristic within a single file, which
covers fewer than 10% of real violations. `BindingContext` resolves any call expression to its
exact declaration, enabling precise cross-file, cross-module detection with no false positives
from name collisions.

#### Gradle setup

`UncheckedFlagAccess` must run under `detektWithTypeResolution` (not the plain `detekt` task).
For KMP projects, each compilation target has its own task:

```
./gradlew detektWithTypeResolutionAndroidMain
./gradlew detektWithTypeResolutionCommonMain
./gradlew detektWithTypeResolutionJvmMain
```

The rule must guard against running without type resolution. If
`bindingContext == BindingContext.EMPTY`, skip all checks and log a warning:

```kotlin
override fun visitCallExpression(expression: KtCallExpression) {
    if (bindingContext == BindingContext.EMPTY) return
    // ...
}
```

#### Detection algorithm

For each `KtCallExpression` and `KtNameReferenceExpression`:

1. Resolve to declaration via `BindingContext`:
   ```kotlin
   val descriptor = expression.getResolvedCall(bindingContext)
       ?.resultingDescriptor ?: return
   ```
2. Look up `@BehindFlag` on the descriptor:
   ```kotlin
   val annotation = descriptor.annotations
       .findAnnotation(FqName("dev.androidbroadcast.featured.BehindFlag")) ?: return
   val flagName = annotation.allValueArguments[Name.identifier("flagName")]
       ?.value as? String ?: return
   ```
3. Walk up the PSI tree from the call site to find a valid context (see table below).
4. No valid context found → `report(CodeSmell(...))`.

#### Valid contexts

| Context | How it's detected |
|---|---|
| Direct `if` check | An enclosing `KtIfExpression` condition subtree contains a `KtNameReferenceExpression` with `getReferencedName() == flagName` (via `PsiTreeUtil.findChildrenOfType`) |
| Direct `when` check | An enclosing `KtWhenEntry` condition subtree contains a `KtNameReferenceExpression` with `getReferencedName() == flagName` |
| Propagated flag context | An enclosing `KtNamedFunction` or `KtClass` has `@BehindFlag("X")` with the same `flagName` (checked via PSI annotation entries — no BindingContext needed for the container) |
| Explicit assumption | An enclosing `KtNamedFunction` or `KtClass` has `@AssumesFlag("X")` with the same `flagName` (PSI annotation entries) |

Condition matching scans the full condition subtree. This correctly handles all common patterns:

```kotlin
if (newCheckout) { ... }                      // bare reference        ✅
if (configValues[newCheckout]) { ... }         // array access          ✅
if (configValues.get(newCheckout)) { ... }     // call argument         ✅
if (featureFlags.newCheckout) { ... }          // dot-qualified         ✅
if (isNewCheckoutEnabled()) { ... }            // renamed helper        ❌ out of scope
```

#### Scope by annotation target

| Target | What is checked |
|---|---|
| `FUNCTION` | Every `KtCallExpression` resolving to the annotated function |
| `CLASS` | Every `KtCallExpression` resolving to the primary constructor of the annotated class |
| `PROPERTY` | Every `KtNameReferenceExpression` resolving to the annotated property (via `BindingContext.REFERENCE_TARGET`) |

Method calls on instances of an annotated class are **not** checked.
Secondary constructors are **not** checked.

---

### `InvalidFlagReference`

**Severity:** Warning
**Debt:** `Debt.FIVE_MINS`

Fires when `@BehindFlag("X")` or `@AssumesFlag("X")` references a flag name with no matching
`@LocalFlag` or `@RemoteFlag` property in the same file.

This rule is **PSI-only** (no type resolution required) and runs under the plain `detekt` task.
Cross-module flag registry validation is out of scope. Teams that co-locate flag declarations
with annotated code get full validation; others get no false positives.

#### Algorithm (two-pass, per-file)

```kotlin
override fun visitFile(file: KtFile) {
    // Pass 1: collect @LocalFlag / @RemoteFlag property names in this file
    val knownFlags = file.collectDescendantsOfType<KtProperty>()
        .filter { it.hasAnnotation("LocalFlag") || it.hasAnnotation("RemoteFlag") }
        .mapNotNull { it.name }
        .toSet()

    // Pass 2: validate @BehindFlag / @AssumesFlag annotation arguments
    file.collectDescendantsOfType<KtAnnotationEntry>()
        .filter { it.shortName?.asString() in setOf("BehindFlag", "AssumesFlag") }
        .forEach { annotation ->
            val flagName = annotation.valueArguments
                .firstOrNull()?.getArgumentExpression()?.text?.trim('"') ?: return@forEach
            if (flagName !in knownFlags) report(CodeSmell(...))
        }
}
```

Two passes avoid ordering sensitivity (annotation before declaration in same file).

---

## `FeaturedRuleSetProvider` updates

Register both new rules in `FeaturedRuleSetProvider.instance(config: Config)`. Update the
KDoc to include both rules in the `detekt.yml` example block. Note in the KDoc that
`UncheckedFlagAccess` requires `detektWithTypeResolution`.

---

## Corner Cases

| Situation | Behaviour |
|---|---|
| `@BehindFlag("A")` called inside `@BehindFlag("B")` context | ❌ Warning — different flags |
| Lambda: `val f = { NewCheckoutScreen() }` | ❌ Warning — not a guarded context |
| `@AssumesFlag` without actual flag check inside | ✅ No warning — escape hatch |
| `@AssumesFlag` on CLASS — member functions / `init` | ✅ No warning |
| `@AssumesFlag` on CLASS — companion object members | ❌ Warning — excluded from scope |
| Annotated class subclassed / interface implemented | Annotation not inherited |
| Secondary constructor of `@BehindFlag` class | Not checked — out of scope |
| `UncheckedFlagAccess` run without type resolution | Skips silently — logs warning |
| Call site and declaration in different files / modules | ✅ Detected via BindingContext |

---

## File Map

```
core/
  src/commonMain/kotlin/dev/androidbroadcast/featured/
    BehindFlag.kt          ← new
    AssumesFlag.kt         ← new

featured-detekt-rules/
  src/main/kotlin/dev/androidbroadcast/featured/detekt/
    UncheckedFlagAccess.kt     ← new (requires type resolution)
    InvalidFlagReference.kt    ← new (PSI-only)
    FeaturedRuleSetProvider.kt ← register 2 new rules + update KDoc

  src/test/kotlin/dev/androidbroadcast/featured/detekt/
    UncheckedFlagAccessTest.kt    ← new
    InvalidFlagReferenceTest.kt   ← new
```

---

## detekt.yml additions

```yaml
featured:
  UncheckedFlagAccess:
    active: true      # requires detektWithTypeResolution task
  InvalidFlagReference:
    active: true      # runs under plain detekt task
```

---

## Testing Requirements

**`UncheckedFlagAccessTest`**

Tests use `LintTestRule` with `KotlinCoreEnvironment` to enable type resolution.

| Scenario | Expected |
|---|---|
| `if (newCheckout)` before call | No finding |
| `if (configValues[newCheckout])` before call | No finding |
| `if (featureFlags.newCheckout)` before call | No finding |
| `when` with flag name in condition | No finding |
| Call inside `@BehindFlag("X")` function, same flag | No finding |
| Call inside `@AssumesFlag("X")` function, same flag | No finding |
| Call inside `@AssumesFlag("X")` class body, same flag | No finding |
| `@BehindFlag` code calling other `@BehindFlag` code, same flag | No finding |
| Declaration in file A, call site in file B (cross-file) | Finding |
| Call at top level, no context | Finding |
| Call inside `@BehindFlag("Y")`, different flag | Finding |
| Call inside `@AssumesFlag("Y")`, different flag | Finding |
| Lambda: `val f = { NewCheckoutScreen() }` | Finding |
| `@BehindFlag` on class: constructor called without context | Finding |
| `@BehindFlag` on class: constructor inside valid `if` | No finding |
| `@BehindFlag` on property: access without context | Finding |
| `@AssumesFlag` on class: companion object member calls flagged code | Finding |
| Rule run with `BindingContext.EMPTY` (no type resolution) | No finding, no crash |

**`InvalidFlagReferenceTest`**

| Scenario | Expected |
|---|---|
| `@BehindFlag("newCheckout")` + `@LocalFlag val newCheckout` same file | No finding |
| `@BehindFlag("newChekout")` typo, correct property present | Finding |
| `@AssumesFlag("unknown")` on function | Finding |
| `@AssumesFlag("unknown")` on class | Finding |
| `@BehindFlag("remoteFlag")` + `@RemoteFlag val remoteFlag` same file | No finding |
| Flag registry in a different file, no local declaration | No finding — no false positive |
| `@BehindFlag` appears before `@LocalFlag` declaration in same file | No finding — two-pass |
