# Design: `@BehindFlag` / `@AssumesFlag` — Feature Flag Guard Annotations

**Date:** 2026-03-23
**Status:** Draft
**Modules affected:** `core`, `featured-detekt-rules`

---

## Problem

There is no static mechanism to enforce that code intended to run only when a feature flag is enabled is actually guarded by a flag check at every call site. A developer can introduce a new screen, class, or function behind a flag on their branch, but nothing prevents a colleague from calling that code without checking the flag first — the mistake is invisible until runtime.

---

## Goals

1. Provide a way to annotate code that must only be used when a specific feature flag is active.
2. Provide a way to declare that a calling context already guarantees the flag is checked, suppressing redundant warnings downstream.
3. Catch violations at lint time (Detekt), before code reaches review or CI.
4. Validate that flag name strings in annotations refer to real declared flags, catching typos at lint time.
5. Zero runtime overhead — annotation-only, no reflection or wrapper types.

---

## Non-Goals

- Transitive call-chain analysis across arbitrary depth (only direct syntactic context is checked).
- Tracking method calls on an already-constructed instance of an annotated class.
- Lambda / functional type analysis.
- Runtime enforcement.

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
     * The [InvalidFlagReference] Detekt rule validates that a property with this name
     * and a `@LocalFlag` or `@RemoteFlag` annotation exists in the project.
     */
    val flagName: String,
)
```

**KDoc requirement:** Full KDoc with:
- Purpose and intended workflow.
- `@BehindFlag` / `@AssumesFlag` cross-reference.
- Non-compliant and compliant usage examples.

### `@AssumesFlag`

Marks a function or class that takes explicit responsibility for ensuring the named flag is checked before execution. Call sites inside the annotated scope are not warned by `UncheckedFlagAccess`.

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class AssumesFlag(
    /**
     * The name of the feature flag property that this scope guarantees is checked
     * before the annotated code runs. Must match the `flagName` of the corresponding
     * `@BehindFlag` declaration.
     *
     * This is an escape hatch — the developer asserts correctness. There is no
     * automated check that the flag is actually verified inside the annotated scope.
     */
    val flagName: String,
)
```

**KDoc requirement:** Full KDoc with an explicit warning that this is an escape hatch and the developer bears responsibility for correctness.

---

## Detekt Rules (`featured-detekt-rules` module)

### `UncheckedFlagAccess`

**Severity:** Warning
**Debt:** 10 min

Fires when a call to a `@BehindFlag("X")`-annotated function, constructor, or property access is found outside a valid context.

#### Valid contexts (checked by walking up the PSI tree from the call site)

| Context | How it's detected |
|---|---|
| Direct `if`/`when` check | The condition text of an enclosing `KtIfExpression` or `KtWhenEntry` contains `flagName` as a word |
| Propagated flag context | An enclosing `KtNamedFunction` or `KtClass` carries `@BehindFlag("X")` for the same `flagName` |
| Explicit assumption | An enclosing `KtNamedFunction` or `KtClass` carries `@AssumesFlag("X")` for the same `flagName` |

The rule walks upward through enclosing PSI nodes and accepts the call site as soon as **any** valid context is found. If the root of the file is reached without finding one, a `CodeSmell` is reported.

#### Scope by annotation target

| Target | What is checked |
|---|---|
| `FUNCTION` | Every call expression whose callee name matches the annotated function |
| `CLASS` | Every constructor call (`ClassName(...)`) of the annotated class |
| `PROPERTY` | Every name reference to the annotated property |

Method calls on an already-constructed instance of an annotated class are **not** checked — this requires full type resolution, which is out of scope for this rule.

#### Implementation note

The rule operates without full type resolution (consistent with the existing rules in this module). Detection is name-based and heuristic:

1. During file visits, collect all function/class/property names annotated with `@BehindFlag`.
2. When visiting call expressions and name references, check if the callee name is in the collected set.
3. Walk up the PSI tree to validate the context.

False positives from name collisions can be suppressed with `@Suppress("UncheckedFlagAccess")`.

#### KDoc requirement

Full KDoc with non-compliant and compliant code examples, following the style of `HardcodedFlagValueRule`.

---

### `InvalidFlagReference`

**Severity:** Warning
**Debt:** 5 min

Fires when `@BehindFlag("X")` or `@AssumesFlag("X")` references a flag name that does not correspond to any known `@LocalFlag` or `@RemoteFlag` property in the project.

#### Algorithm (two-pass across all files)

1. **Pass 1** — Collect all property names annotated with `@LocalFlag` or `@RemoteFlag`.
2. **Pass 2** — For every `@BehindFlag` and `@AssumesFlag` annotation, check that `flagName` is present in the collected set. If not, report a `CodeSmell` on the annotation.

#### KDoc requirement

Full KDoc with non-compliant (typo) and compliant examples.

---

## Corner Cases

| Situation | Behaviour |
|---|---|
| `@BehindFlag("A")` called inside `@BehindFlag("B")` context | ❌ Warning — different flags, no valid context |
| Lambda capturing flagged code: `val f = { NewCheckoutScreen() }` | ❌ Warning — lambda body is not a guarded context |
| `@AssumesFlag` without an actual flag check inside the scope | ✅ No warning — escape hatch; correctness is the developer's responsibility |
| Annotated class subclassed or interface implemented | Annotation is not inherited — only the directly annotated declaration is tracked |
| Same function name in two different classes | Possible false positive — suppress with `@Suppress("UncheckedFlagAccess")` |

---

## File Map

```
core/
  src/commonMain/kotlin/dev/androidbroadcast/featured/
    BehindFlag.kt          ← new
    AssumesFlag.kt         ← new

featured-detekt-rules/
  src/main/kotlin/dev/androidbroadcast/featured/detekt/
    UncheckedFlagAccess.kt    ← new
    InvalidFlagReference.kt   ← new
    FeaturedRuleSetProvider.kt ← register 2 new rules

  src/test/kotlin/dev/androidbroadcast/featured/detekt/
    UncheckedFlagAccessTest.kt   ← new
    InvalidFlagReferenceTest.kt  ← new
```

---

## detekt.yml additions

```yaml
featured:
  UncheckedFlagAccess:
    active: true
  InvalidFlagReference:
    active: true
```

---

## Testing Requirements

Each rule must have unit tests covering:

**`UncheckedFlagAccess`**
- Direct `if` check with flag name → no finding
- Direct `when` check with flag name → no finding
- Call inside `@BehindFlag("X")` function → no finding
- Call inside `@AssumesFlag("X")` function → no finding
- Call at top level without any context → finding
- Call inside `@BehindFlag("Y")` (different flag) → finding
- Lambda body → finding
- `@BehindFlag` on class, constructor called without context → finding
- `@BehindFlag` on property, accessed without context → finding

**`InvalidFlagReference`**
- `@BehindFlag("newCheckout")` with matching `@LocalFlag val newCheckout` → no finding
- `@BehindFlag("newChekout")` (typo) → finding
- `@AssumesFlag("unknown")` → finding
