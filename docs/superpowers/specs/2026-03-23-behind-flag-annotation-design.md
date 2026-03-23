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
4. Validate that flag name strings in annotations refer to real declared flags, catching typos at lint time (within the same file).
5. Zero runtime overhead — annotation-only, no reflection or wrapper types.

---

## Non-Goals

- Transitive call-chain analysis across arbitrary depth (only direct syntactic context is checked).
- Tracking method calls on an already-constructed instance of an annotated class.
- Lambda / functional type analysis.
- Secondary constructor guarding (`@BehindFlag` on a class covers primary constructor calls only).
- Runtime enforcement.
- Cross-file validation of flag name strings in `InvalidFlagReference` (same-file only; see rule spec below).
- Detecting renamed boolean helpers (`if (isNewCheckoutEnabled())`) — only direct flag property name references in conditions are matched.

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
     * and a `@LocalFlag` or `@RemoteFlag` annotation exists in the **same file** as
     * this annotation. For flag registries defined in separate files, the rule does
     * not produce false positives — it simply does not validate.
     *
     * See also: [AssumesFlag]
     */
    val flagName: String,
)
```

**KDoc model:** Follow the style of `@ExpiresAt` — single-paragraph description, intended
workflow as a numbered list, a usage code block, and a `See also` cross-reference to
`@AssumesFlag`. The KDoc must also explicitly state the same-file limitation of
`InvalidFlagReference`.

**Coverage note:** Annotation classes with no body generate no coverable lines. They do not
affect the `core` module's ≥90% line coverage requirement and require no test coverage.

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
     * The name of the feature flag property that this scope guarantees is checked
     * before the annotated code runs. Must match the `flagName` of the corresponding
     * `@BehindFlag` declaration.
     *
     * **This is an escape hatch.** The Detekt rule trusts this annotation without
     * verifying that an actual flag check exists inside the scope. The developer
     * asserts correctness. Misuse silently bypasses `UncheckedFlagAccess`.
     *
     * See also: [BehindFlag]
     */
    val flagName: String,
)
```

**KDoc model:** Same style as `@BehindFlag`. Must include an explicit warning paragraph about
the escape-hatch nature and absence of automated verification inside the scope.

**Scope of `@AssumesFlag` on CLASS:** "inside the annotated scope" means any member function
or `init` block within the class body. Companion object members are **excluded** — they are
a separate syntactic scope in PSI (`KtObjectDeclaration` child, not a member of the class).

---

## Detekt Rules (`featured-detekt-rules` module)

### `UncheckedFlagAccess`

**Severity:** Warning
**Debt:** `Debt.TWENTY_MINS`

Fires when a call to a `@BehindFlag("X")`-annotated function, constructor, or property access
is found outside a valid context.

#### Scope: same-file only

`UncheckedFlagAccess` operates within a single file, consistent with all other rules in this
module and with `InvalidFlagReference`. Cross-file detection would require type resolution
(`BindingContext`) to obtain fully qualified names and is out of scope for this iteration.

When a `@BehindFlag`-annotated declaration and its call site are in different files, the rule
produces no warning and no false positive. This is an accepted limitation.

The accumulator (`MutableMap<String, String>`) is **file-scoped**: populated at the start of
each file visit and cleared between files. It maps simple declaration name to `flagName`:

```
declarationName → flagName
e.g. "NewCheckoutScreen" → "newCheckout"
     "NewCheckoutViewModel" → "newCheckout"
     "checkoutConfig" → "newCheckout"
```

Because the scope is limited to one file, simple name keys are sufficient — there are no
cross-file collisions. Same-file name collisions (two unrelated functions with the same name)
remain possible but are rare in practice. Suppress with `@Suppress("UncheckedFlagAccess")`.

The accumulator is populated via a pre-pass over the file before call site validation (same
two-pass pattern as `InvalidFlagReference`) to avoid ordering sensitivity.

#### Valid contexts (checked by walking up the PSI tree from the call site)

| Context | How it's detected |
|---|---|
| Direct `if` check | An enclosing `KtIfExpression` condition subtree contains a `KtNameReferenceExpression` with `getReferencedName() == flagName` |
| Direct `when` check | An enclosing `KtWhenEntry` condition subtree contains a `KtNameReferenceExpression` with `getReferencedName() == flagName` |
| Propagated flag context | An enclosing `KtNamedFunction` or `KtClass` carries `@BehindFlag("X")` for the same `flagName` |
| Explicit assumption | An enclosing `KtNamedFunction` or `KtClass` carries `@AssumesFlag("X")` for the same `flagName` |

Condition matching scans the **entire subtree** of the condition expression for
`KtNameReferenceExpression` nodes using `PsiTreeUtil.findChildrenOfType`. This correctly
handles all common patterns:

```kotlin
if (newCheckout) { ... }                    // bare reference
if (configValues[newCheckout]) { ... }      // array access subscript
if (configValues.get(newCheckout)) { ... }  // call argument
if (featureFlags.newCheckout) { ... }       // dot-qualified selector
```

Patterns that are intentionally NOT matched (out of scope):
```kotlin
if (isNewCheckoutEnabled()) { ... }         // renamed boolean helper
if (newCheckoutEnabled) { ... }             // extracted local boolean
```

The rule walks upward through enclosing PSI nodes and accepts the call site as soon as any
valid context is found. If the root of the file is reached without finding one, a `CodeSmell`
is reported.

#### Scope by annotation target

| Target | What is checked |
|---|---|
| `FUNCTION` | Every `KtCallExpression` whose callee name matches an entry in the accumulator |
| `CLASS` | Every `KtCallExpression` that is a constructor call (`ClassName(...)`) for a class in the accumulator |
| `PROPERTY` | Every `KtNameReferenceExpression` whose `getReferencedName()` matches an entry in the accumulator, excluding call expression callees (already handled above) |

**False positive risk for PROPERTY:** A property name like `enabled` will match any reference
to any local variable or parameter of the same name. This is the same heuristic trade-off
accepted by the existing rules. Suppress with `@Suppress("UncheckedFlagAccess")` at the
call site. Prefer specific, unique property names for `@BehindFlag`-annotated properties.

Method calls on an already-constructed instance of an annotated class are **not** checked.
Secondary constructors are **not** checked (out of scope — see Non-Goals).

#### KDoc requirement

Full KDoc with non-compliant and compliant code examples, following the style of
`HardcodedFlagValueRule`. Include a note on the ordering caveat for cross-file detection.

---

### `InvalidFlagReference`

**Severity:** Warning
**Debt:** `Debt.FIVE_MINS`

Fires when `@BehindFlag("X")` or `@AssumesFlag("X")` references a flag name that does not
correspond to any `@LocalFlag` or `@RemoteFlag` property **in the same file**.

#### Scope limitation

Cross-file validation is not supported. If the flag registry is in a different file, the rule
produces no warning and no false positive. Teams that co-locate flag declarations and annotated
code get full validation.

#### Algorithm (two-pass, per-file)

To avoid ordering sensitivity (annotation appears before property declaration in the file),
the rule performs two explicit PSI traversals within `visitFile`:

```kotlin
override fun visitFile(file: KtFile) {
    // Pass 1: collect @LocalFlag / @RemoteFlag property names in this file
    val knownFlags = mutableSetOf<String>()
    file.collectDescendantsOfType<KtProperty>()
        .filter { it.hasAnnotation("LocalFlag") || it.hasAnnotation("RemoteFlag") }
        .mapNotNullTo(knownFlags) { it.name }

    // Pass 2: validate @BehindFlag / @AssumesFlag annotation arguments
    file.collectDescendantsOfType<KtAnnotationEntry>()
        .filter { it.shortName?.asString() in setOf("BehindFlag", "AssumesFlag") }
        .forEach { annotation ->
            val flagName = annotation.valueArguments
                .firstOrNull()?.getArgumentExpression()?.text?.trim('"')
                ?: return@forEach
            if (flagName !in knownFlags) {
                report(CodeSmell(...))
            }
        }
}
```

#### KDoc requirement

Full KDoc with non-compliant (typo) and compliant examples, following the style of
`MissingFlagAnnotationRule`.

---

## `FeaturedRuleSetProvider` updates

Register both new rules in `FeaturedRuleSetProvider.instance(config: Config)`. **Also update
the KDoc** of `FeaturedRuleSetProvider` to include both new rules in the `detekt.yml` example
block, keeping the documented example consistent with the actual rule set.

---

## Corner Cases

| Situation | Behaviour |
|---|---|
| `@BehindFlag("A")` called inside `@BehindFlag("B")` context | ❌ Warning — different flags, no valid context |
| Lambda capturing flagged code: `val f = { NewCheckoutScreen() }` | ❌ Warning — lambda body is not a guarded context |
| `@AssumesFlag` on a function without an actual flag check inside | ✅ No warning — escape hatch; developer's responsibility |
| `@AssumesFlag` on CLASS — member functions and `init` blocks | ✅ No warning inside class body |
| `@AssumesFlag` on CLASS — companion object members | ❌ Warning — companion object is excluded from scope |
| Annotated class subclassed or interface implemented | Annotation not inherited — only directly annotated declaration tracked |
| Same function/property name in two different classes | Possible false positive — suppress with `@Suppress("UncheckedFlagAccess")` |
| Secondary constructor of `@BehindFlag` class | Out of scope — not checked |
| Declaration and call site in different files | Out of scope — no warning, no false positive |

---

## File Map

```
core/
  src/commonMain/kotlin/dev/androidbroadcast/featured/
    BehindFlag.kt          ← new
    AssumesFlag.kt         ← new

featured-detekt-rules/
  src/main/kotlin/dev/androidbroadcast/featured/detekt/
    UncheckedFlagAccess.kt     ← new
    InvalidFlagReference.kt    ← new
    FeaturedRuleSetProvider.kt ← register 2 new rules + update KDoc example

  src/test/kotlin/dev/androidbroadcast/featured/detekt/
    UncheckedFlagAccessTest.kt    ← new
    InvalidFlagReferenceTest.kt   ← new
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

**`UncheckedFlagAccessTest`**

| Scenario | Expected |
|---|---|
| Direct `if (newCheckout)` before call | No finding |
| Direct `if (configValues[newCheckout])` before call | No finding |
| Direct `if (featureFlags.newCheckout)` before call | No finding |
| Direct `when` with flag name in condition | No finding |
| Call inside `@BehindFlag("X")` function, same flag | No finding |
| Call inside `@AssumesFlag("X")` function, same flag | No finding |
| Call inside `@AssumesFlag("X")` class member function, same flag | No finding |
| `@BehindFlag` code calling other `@BehindFlag` code, same flag | No finding |
| Call at top level, no context | Finding |
| Call inside `@BehindFlag("Y")` function, different flag | Finding |
| Call inside `@AssumesFlag("Y")` function, different flag | Finding |
| Lambda body: `val f = { NewCheckoutScreen() }` | Finding |
| `@BehindFlag` on class: constructor called without context | Finding |
| `@BehindFlag` on class: constructor inside valid `if` | No finding |
| `@BehindFlag` on property: access without context | Finding |
| `@AssumesFlag` on class: companion object member calls flagged code | Finding |
| Declaration and call site in same file, call site appears before declaration | No finding — two-pass handles ordering |
| Declaration and call site in different files | No finding — out of scope, no false positive |

**`InvalidFlagReferenceTest`**

| Scenario | Expected |
|---|---|
| `@BehindFlag("newCheckout")` + `@LocalFlag val newCheckout` same file | No finding |
| `@BehindFlag("newChekout")` (typo) + `@LocalFlag val newCheckout` same file | Finding |
| `@AssumesFlag("unknown")` on function, no matching property | Finding |
| `@AssumesFlag("unknown")` on class, no matching property | Finding |
| `@BehindFlag("remoteFlag")` + `@RemoteFlag val remoteFlag` same file | No finding |
| `@BehindFlag("newCheckout")` + flag declared in a different file (no local declaration) | No finding — no false positive |
| `@BehindFlag` appears before `@LocalFlag` declaration in same file | No finding — two-pass handles ordering |
