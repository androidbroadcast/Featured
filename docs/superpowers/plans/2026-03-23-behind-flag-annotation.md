# @BehindFlag / @AssumesFlag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `@BehindFlag` and `@AssumesFlag` annotations to `core` and two Detekt rules to `featured-detekt-rules` that enforce flag-guarded usage at lint time.

**Architecture:** Two SOURCE-retention annotations in `core` mark code that must be used behind a flag (`@BehindFlag`) and scopes that take responsibility for the check (`@AssumesFlag`). Two Detekt rules enforce this: `InvalidFlagReference` (PSI-only, validates flag name strings in same file) and `UncheckedFlagAccess` (type resolution via BindingContext, cross-file call-site validation).

**Tech Stack:** Kotlin, Detekt 1.23.8 (`detekt-api`, `detekt-test`), KMP `core` module, JVM `featured-detekt-rules` module.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `core/src/commonMain/kotlin/dev/androidbroadcast/featured/BehindFlag.kt` | Create | `@BehindFlag` annotation |
| `core/src/commonMain/kotlin/dev/androidbroadcast/featured/AssumesFlag.kt` | Create | `@AssumesFlag` annotation |
| `featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReference.kt` | Create | PSI-only rule — validates flag name strings |
| `featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccess.kt` | Create | Type-resolution rule — validates call sites cross-file |
| `featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/FeaturedRuleSetProvider.kt` | Modify | Register 2 new rules + update KDoc |
| `featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReferenceTest.kt` | Create | Tests for `InvalidFlagReference` |
| `featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccessTest.kt` | Create | Tests for `UncheckedFlagAccess` |

---

## Task 1: `@BehindFlag` annotation

**Files:**
- Create: `core/src/commonMain/kotlin/dev/androidbroadcast/featured/BehindFlag.kt`

- [ ] **Step 1: Create `BehindFlag.kt`**

```kotlin
package dev.androidbroadcast.featured

/**
 * Marks a function, class, or property that must only be used inside a valid feature-flag
 * context — i.e., where the named flag has been checked before execution.
 *
 * ## Intended workflow
 *
 * 1. **Declare the flag** — introduce a `ConfigParam` annotated with `@LocalFlag` or
 *    `@RemoteFlag`. The `flagName` here must match the exact Kotlin property name.
 * 2. **Annotate guarded code** — place `@BehindFlag("flagName")` on every function, class,
 *    or property that must only run when the flag is active.
 * 3. **Guard call sites** — wrap every call site in an `if`/`when` that checks the flag,
 *    or annotate the containing function/class with `@AssumesFlag("flagName")`.
 * 4. **Let Detekt enforce it** — the `UncheckedFlagAccess` rule (requires
 *    `detektWithTypeResolution`) reports any call site that lacks a valid guard.
 *
 * ## Usage
 *
 * ```kotlin
 * @LocalFlag
 * val newCheckout = ConfigParam<Boolean>("new_checkout", defaultValue = false)
 *
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() { ... }
 *
 * // Call site must be guarded:
 * if (configValues[newCheckout]) {
 *     NewCheckoutScreen()
 * }
 * ```
 *
 * This annotation has [AnnotationRetention.SOURCE] retention — zero runtime overhead.
 *
 * @see AssumesFlag
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class BehindFlag(
    /**
     * The name of the Kotlin property (declared with `@LocalFlag` or `@RemoteFlag`)
     * that guards this declaration. Must match the exact property name, e.g. `"newCheckout"`.
     *
     * Validated by the `InvalidFlagReference` Detekt rule within the same file.
     */
    val flagName: String,
)
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :core:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/commonMain/kotlin/dev/androidbroadcast/featured/BehindFlag.kt
git commit -m "feat(core): add @BehindFlag annotation"
```

---

## Task 2: `@AssumesFlag` annotation

**Files:**
- Create: `core/src/commonMain/kotlin/dev/androidbroadcast/featured/AssumesFlag.kt`

- [ ] **Step 1: Create `AssumesFlag.kt`**

```kotlin
package dev.androidbroadcast.featured

/**
 * Marks a function or class that takes explicit responsibility for ensuring the named feature
 * flag is checked before execution reaches this scope.
 *
 * ## Purpose
 *
 * When a function or class always runs within a guarded context but cannot express that guard
 * directly in its own body (e.g., a navigation host that conditionally renders flag-gated
 * screens), annotate it with `@AssumesFlag` to suppress `UncheckedFlagAccess` warnings for
 * call sites of `@BehindFlag`-annotated code inside this scope.
 *
 * ## Scope
 *
 * - On a **function**: suppresses warnings inside the function body.
 * - On a **class**: suppresses warnings inside member functions and `init` blocks.
 *   Companion object members are **not** covered — they are a separate scope.
 *
 * ## ⚠️ Escape hatch
 *
 * This annotation is **not verified**. The Detekt rule trusts the annotation without
 * checking that an actual flag guard exists inside the scope. Misuse silently bypasses
 * `UncheckedFlagAccess`. Use it only when the calling context genuinely guarantees the
 * flag is checked.
 *
 * ## Usage
 *
 * ```kotlin
 * @AssumesFlag("newCheckout")
 * fun CheckoutNavHost(configValues: ConfigValues) {
 *     // This function is only called when newCheckout is enabled upstream.
 *     NewCheckoutScreen()  // no UncheckedFlagAccess warning here
 * }
 * ```
 *
 * This annotation has [AnnotationRetention.SOURCE] retention — zero runtime overhead.
 *
 * @see BehindFlag
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class AssumesFlag(
    /**
     * The name of the feature flag property this scope guarantees is checked before execution.
     * Must match the `flagName` of the corresponding `@BehindFlag` declaration.
     */
    val flagName: String,
)
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :core:compileKotlinJvm
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/commonMain/kotlin/dev/androidbroadcast/featured/AssumesFlag.kt
git commit -m "feat(core): add @AssumesFlag annotation"
```

---

## Task 3: `InvalidFlagReference` rule

PSI-only rule. No type resolution needed. Uses the same `rule.lint()` test pattern as all
existing rules in this module.

**Files:**
- Create: `featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReference.kt`
- Create: `featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReferenceTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReferenceTest.kt
package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Test
import kotlin.test.assertEquals

class InvalidFlagReferenceTest {
    private val rule = InvalidFlagReference()

    @Test
    fun `no finding when BehindFlag matches LocalFlag property in same file`() {
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val newCheckout = ConfigParam("new_checkout", false)

            @BehindFlag("newCheckout")
            fun NewCheckoutScreen() {}
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag matches RemoteFlag property in same file`() {
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.RemoteFlag

            @RemoteFlag
            val remoteFeature = ConfigParam("remote_feature", false)

            @BehindFlag("remoteFeature")
            fun RemoteFeatureScreen() {}
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports finding when BehindFlag has typo in flag name`() {
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val newCheckout = ConfigParam("new_checkout", false)

            @BehindFlag("newChekout")
            fun NewCheckoutScreen() {}
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when AssumesFlag references unknown flag on function`() {
        // @LocalFlag must be present so knownFlags is non-empty; "unknown" is not in it
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.AssumesFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val realFlag = ConfigParam("real_flag", false)

            @AssumesFlag("unknown")
            fun SomeContainer() {}
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when AssumesFlag references unknown flag on class`() {
        // @LocalFlag must be present so knownFlags is non-empty; "unknown" is not in it
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.AssumesFlag
            import dev.androidbroadcast.featured.LocalFlag

            @LocalFlag
            val realFlag = ConfigParam("real_flag", false)

            @AssumesFlag("unknown")
            class SomeViewModel {}
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `no finding when flag registry is in a different file`() {
        // No @LocalFlag or @RemoteFlag in this snippet — rule must not false-positive
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun NewCheckoutScreen() {}
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag annotation appears before LocalFlag declaration`() {
        // Two-pass must handle ordering correctly
        val findings = rule.lint("""
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.LocalFlag

            @BehindFlag("newCheckout")
            fun NewCheckoutScreen() {}

            @LocalFlag
            val newCheckout = ConfigParam("new_checkout", false)
        """.trimIndent())
        assertEquals(0, findings.size)
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :featured-detekt-rules:test --tests "*.InvalidFlagReferenceTest"
```
Expected: compilation error — `InvalidFlagReference` does not exist yet.

- [ ] **Step 3: Create `InvalidFlagReference.kt`**

```kotlin
// featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReference.kt
package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Warns when `@BehindFlag` or `@AssumesFlag` references a flag name that has no matching
 * `@LocalFlag` or `@RemoteFlag` property in the same file.
 *
 * This catches typos in `flagName` at lint time. If the flag registry lives in a different
 * file, the rule produces no warning (no false positives).
 *
 * **Non-compliant:**
 * ```kotlin
 * @BehindFlag("newChekout")  // typo
 * fun NewCheckoutScreen() {}
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * @LocalFlag
 * val newCheckout = ConfigParam("new_checkout", false)
 *
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() {}
 * ```
 */
public class InvalidFlagReference(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue: Issue = Issue(
        id = "InvalidFlagReference",
        severity = Severity.Warning,
        description = "@BehindFlag or @AssumesFlag references an unknown flag name.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitFile(file: KtFile) {
        // Pass 1: collect @LocalFlag / @RemoteFlag property names in this file
        val knownFlags = file.collectDescendantsOfType<KtProperty>()
            .filter { property ->
                property.annotationEntries.any {
                    it.shortName?.asString() in setOf("LocalFlag", "RemoteFlag")
                }
            }
            .mapNotNull { it.name }
            .toSet()

        // No local flag declarations — nothing to validate against, skip to avoid false positives
        if (knownFlags.isEmpty()) return

        // Pass 2: validate @BehindFlag / @AssumesFlag annotation arguments
        file.collectDescendantsOfType<KtAnnotationEntry>()
            .filter { it.shortName?.asString() in setOf("BehindFlag", "AssumesFlag") }
            .forEach { annotation ->
                val flagName = annotation.valueArguments
                    .firstOrNull()
                    ?.getArgumentExpression()
                    ?.text
                    ?.trim('"')
                    ?: return@forEach

                if (flagName !in knownFlags) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(annotation),
                            message = "Flag name '$flagName' does not match any @LocalFlag or " +
                                "@RemoteFlag property in this file.",
                        )
                    )
                }
            }
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./gradlew :featured-detekt-rules:test --tests "*.InvalidFlagReferenceTest"
```
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 5: Commit**

```bash
git add featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReference.kt \
        featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/InvalidFlagReferenceTest.kt
git commit -m "feat(detekt): add InvalidFlagReference rule"
```

---

## Task 4: `UncheckedFlagAccess` rule

Requires type resolution. Tests use `rule.lintWithContext(env, code)` instead of `rule.lint()`.
`createEnvironment()` and `lintWithContext` are both in `detekt-test` 1.23.8 — no extra
dependency needed.

**Files:**
- Create: `featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccess.kt`
- Create: `featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccessTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccessTest.kt
package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.test.createEnvironment
import io.gitlab.arturbosch.detekt.test.lintWithContext
import org.junit.Test
import kotlin.test.assertEquals

class UncheckedFlagAccessTest {
    private val rule = UncheckedFlagAccess()
    private val env = createEnvironment()

    // ── No findings ──────────────────────────────────────────────────────────

    @Test
    fun `no finding for direct if check with bare flag reference`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host(newCheckout: Boolean) {
                if (newCheckout) { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for if check with array access pattern`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host(configValues: Map<Any, Boolean>, newCheckout: Any) {
                if (configValues[newCheckout] == true) { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for if check with dot-qualified flag reference`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            class Flags { val newCheckout: Boolean = false }

            fun host(featureFlags: Flags) {
                if (featureFlags.newCheckout) { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for when check with flag name in condition`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host(newCheckout: Boolean) {
                when {
                    newCheckout -> newCheckoutScreen()
                }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for call inside BehindFlag function same flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @BehindFlag("newCheckout")
            fun newCheckoutHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for call inside AssumesFlag function same flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            fun checkoutNavHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding for call inside AssumesFlag class member function`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            class CheckoutContainer {
                fun render() { newCheckoutScreen() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `no finding when BehindFlag is silent with BindingContext empty`() {
        // Rule must not crash when run without type resolution
        val findings = UncheckedFlagAccess().lint("""
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    @Test
    fun `reports finding for call at top level without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for call inside BehindFlag function with different flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @BehindFlag("otherFeature")
            fun otherHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for call inside AssumesFlag function with different flag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("otherFeature")
            fun otherHost() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for constructor call without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            class NewCheckoutViewModel

            fun host() { val vm = NewCheckoutViewModel() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `no finding for constructor call inside valid if`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            class NewCheckoutViewModel

            fun host(newCheckout: Boolean) {
                if (newCheckout) { val vm = NewCheckoutViewModel() }
            }
        """.trimIndent())
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports finding for companion object member calling BehindFlag code despite class AssumesFlag`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag
            import dev.androidbroadcast.featured.AssumesFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            @AssumesFlag("newCheckout")
            class CheckoutContainer {
                companion object {
                    fun create() { newCheckoutScreen() }  // companion is excluded
                }
            }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for lambda capturing BehindFlag call`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() {
                val action = { newCheckoutScreen() }  // lambda is not a guarded context
            }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding for BehindFlag property access without context`() {
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            val checkoutConfig: String = "config"

            fun host() {
                val value = checkoutConfig  // unguarded access
            }
        """.trimIndent())
        assertEquals(1, findings.size)
    }

    @Test
    fun `reports finding when call site has no guard — same compilation unit`() {
        // NOTE: Detekt 1.23.8 lintWithContext accepts a single String only.
        // True cross-file detection (declaration in module A, call in module B) cannot be
        // unit-tested here. Verify cross-file behavior manually by:
        //   1. Adding @BehindFlag to a function in :core or any other module
        //   2. Calling it without a guard in :sample or :androidApp
        //   3. Running: ./gradlew detektWithTypeResolutionCommonMain
        //      (or the target-specific variant for the call-site module)
        //   Expected: UncheckedFlagAccess warning reported for the call site.
        val findings = rule.lintWithContext(env, """
            import dev.androidbroadcast.featured.BehindFlag

            @BehindFlag("newCheckout")
            fun newCheckoutScreen() {}

            fun host() { newCheckoutScreen() }
        """.trimIndent())
        assertEquals(1, findings.size)
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
./gradlew :featured-detekt-rules:test --tests "*.UncheckedFlagAccessTest"
```
Expected: compilation error — `UncheckedFlagAccess` does not exist yet.

- [ ] **Step 3: Create `UncheckedFlagAccess.kt`**

```kotlin
// featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccess.kt
package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

private val BEHIND_FLAG_FQN = FqName("dev.androidbroadcast.featured.BehindFlag")
private val ASSUMES_FLAG_FQN = FqName("dev.androidbroadcast.featured.AssumesFlag")
private val FLAG_NAME_PARAM = Name.identifier("flagName")

/**
 * Warns when a `@BehindFlag("X")`-annotated function, constructor, or property is used outside
 * a valid feature-flag context.
 *
 * **Requires type resolution.** Run via `./gradlew detektWithTypeResolution` (or the
 * target-specific variant for KMP: `detektWithTypeResolutionCommonMain`, etc.).
 * When run without type resolution (`BindingContext.EMPTY`), the rule silently skips all
 * checks to avoid false positives.
 *
 * **Valid contexts** (checked by walking up the PSI tree from the call site):
 * - Enclosing `if`/`when` whose condition references the flag by name.
 * - Enclosing function or class annotated `@BehindFlag("X")` for the same flag.
 * - Enclosing function or class annotated `@AssumesFlag("X")` for the same flag.
 *
 * **Non-compliant:**
 * ```kotlin
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() { ... }
 *
 * fun host() { NewCheckoutScreen() }  // missing flag guard
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * if (configValues[newCheckout]) { NewCheckoutScreen() }
 * ```
 */
public class UncheckedFlagAccess(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue: Issue = Issue(
        id = "UncheckedFlagAccess",
        severity = Severity.Warning,
        description = "@BehindFlag-annotated code used outside a feature-flag guard.",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return

        val descriptor = expression.getResolvedCall(bindingContext)
            ?.resultingDescriptor ?: return
        val flagName = descriptor.behindFlagName() ?: return

        if (!expression.isInValidFlagContext(flagName)) {
            report(CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "Call to '${descriptor.name}' is not guarded by flag '$flagName'. " +
                    "Wrap in if/when checking '$flagName', or annotate the containing scope " +
                    "with @BehindFlag(\"$flagName\") or @AssumesFlag(\"$flagName\").",
            ))
        }
    }

    override fun visitNameReferenceExpression(expression: KtNameReferenceExpression) {
        super.visitNameReferenceExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return
        // Skip references that are the callee of a call expression — handled by visitCallExpression
        if (expression.parent is KtCallExpression) return

        val target = bindingContext[BindingContext.REFERENCE_TARGET, expression] ?: return
        val flagName = target.behindFlagName() ?: return

        if (!expression.isInValidFlagContext(flagName)) {
            report(CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "Access to '${expression.getReferencedName()}' is not guarded by " +
                    "flag '$flagName'.",
            ))
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun DeclarationDescriptor.behindFlagName(): String? =
        annotations.findAnnotation(BEHIND_FLAG_FQN)
            ?.allValueArguments
            ?.get(FLAG_NAME_PARAM)
            ?.value as? String

    private fun PsiElement.isInValidFlagContext(flagName: String): Boolean {
        var node: PsiElement? = parent
        while (node != null) {
            when {
                // if (...flagName...) { call() }
                node is KtIfExpression && node.condition.containsFlagReference(flagName) -> return true

                // when { flagName -> { call() } }
                node is KtWhenEntry && node.conditions.any { cond ->
                    cond.containsFlagReference(flagName)
                } -> return true

                // Enclosing function with @BehindFlag("X") or @AssumesFlag("X")
                node is KtNamedFunction && node.hasGuardAnnotation(flagName) -> return true

                // Enclosing class with @BehindFlag("X") or @AssumesFlag("X")
                // but NOT if we crossed a companion object boundary
                node is KtClass && node.hasGuardAnnotation(flagName) -> return true

                // Crossed into a companion object — class annotation does not cover this scope
                node is KtObjectDeclaration && node.isCompanion() -> return false
            }
            node = node.parent
        }
        return false
    }

    private fun PsiElement?.containsFlagReference(flagName: String): Boolean {
        if (this == null) return false
        return PsiTreeUtil.findChildrenOfType(this, KtNameReferenceExpression::class.java)
            .any { it.getReferencedName() == flagName }
    }

    private fun KtNamedFunction.hasGuardAnnotation(flagName: String): Boolean =
        annotationEntries.any { it.matchesGuard(flagName) }

    private fun KtClass.hasGuardAnnotation(flagName: String): Boolean =
        annotationEntries.any { it.matchesGuard(flagName) }

    private fun org.jetbrains.kotlin.psi.KtAnnotationEntry.matchesGuard(flagName: String): Boolean {
        val name = shortName?.asString() ?: return false
        if (name !in setOf("BehindFlag", "AssumesFlag")) return false
        val value = valueArguments.firstOrNull()
            ?.getArgumentExpression()
            ?.text
            ?.trim('"') ?: return false
        return value == flagName
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
./gradlew :featured-detekt-rules:test --tests "*.UncheckedFlagAccessTest"
```
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 5: Commit**

```bash
git add featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccess.kt \
        featured-detekt-rules/src/test/kotlin/dev/androidbroadcast/featured/detekt/UncheckedFlagAccessTest.kt
git commit -m "feat(detekt): add UncheckedFlagAccess rule with type resolution"
```

---

## Task 5: Register rules and update `FeaturedRuleSetProvider`

**Files:**
- Modify: `featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/FeaturedRuleSetProvider.kt`

- [ ] **Step 1: Update `FeaturedRuleSetProvider.kt`**

Replace the entire file with:

```kotlin
package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers the Featured custom Detekt rules under the `featured` rule set id.
 *
 * To enable in your project, add the artifact to Detekt's classpath and include
 * the rule set in your `detekt.yml`:
 *
 * ```yaml
 * featured:
 *   ExpiredFeatureFlag:
 *     active: true
 *   HardcodedFlagValue:
 *     active: true
 *   InvalidFlagReference:
 *     active: true
 *   MissingFlagAnnotation:
 *     active: true
 *   UncheckedFlagAccess:
 *     active: true   # requires detektWithTypeResolution task
 * ```
 *
 * Note: `UncheckedFlagAccess` requires the `detektWithTypeResolution` Gradle task.
 * It silently skips analysis when run under the plain `detekt` task.
 */
public class FeaturedRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "featured"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            id = ruleSetId,
            rules = listOf(
                ExpiredFeatureFlagRule(config),
                HardcodedFlagValueRule(config),
                InvalidFlagReference(config),
                MissingFlagAnnotationRule(config),
                UncheckedFlagAccess(config),
            ),
        )
}
```

- [ ] **Step 2: Run all detekt-rules tests**

```bash
./gradlew :featured-detekt-rules:test
```
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 3: Commit**

```bash
git add featured-detekt-rules/src/main/kotlin/dev/androidbroadcast/featured/detekt/FeaturedRuleSetProvider.kt
git commit -m "feat(detekt): register InvalidFlagReference and UncheckedFlagAccess rules"
```

---

## Task 6: Update `detekt.yml` and run full check

**Files:**
- Modify: project-root `detekt.yml` (wherever it lives — check `grep -r "ExpiredFeatureFlag" .`)

- [ ] **Step 1: Find and update `detekt.yml`**

```bash
grep -r "ExpiredFeatureFlag" /Users/krozov/dev/projects/Featured --include="*.yml" -l
```

Add under the `featured:` block:
```yaml
  InvalidFlagReference:
    active: true
  UncheckedFlagAccess:
    active: true   # requires detektWithTypeResolution
```

- [ ] **Step 2: Run spotless + full build**

```bash
./gradlew spotlessApply
./gradlew :core:build :featured-detekt-rules:build
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run API compatibility check**

```bash
./gradlew :core:apiCheck :featured-detekt-rules:apiCheck
```
If it fails with "API dump is missing", run:
```bash
./gradlew :core:apiDump :featured-detekt-rules:apiDump
```
Then re-check. Commit the updated `.api` files.

- [ ] **Step 4: Final commit**

```bash
git add core/api/ featured-detekt-rules/api/
# Add detekt.yml if it was changed
git add $(git diff --name-only | grep detekt.yml)
git commit -m "chore: update detekt.yml and API dumps for @BehindFlag/@AssumesFlag"
```
