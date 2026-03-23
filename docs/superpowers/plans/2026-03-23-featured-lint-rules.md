# featured-lint-rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a new `:featured-lint-rules` Gradle module with an Android Lint `HardcodedFlagValueDetector` that uses full UAST type resolution to flag direct `.defaultValue` access on `ConfigParam` with zero false positives.

**Architecture:** Plain JVM module (`kotlinJvm` plugin), depends on `lint-api` as `compileOnly`. Detection uses `USimpleNameReferenceExpression` visitor with `JavaEvaluator.extendsClass` for precise type checking. Registry wired via JAR manifest attribute `Lint-Registry-v2`.

**Tech Stack:** Kotlin, `com.android.tools.lint:lint-api:32.1.0`, `com.android.tools.lint:lint-tests:32.1.0`, JUnit 4 (via `lint-tests` transitive dep)

---

## Pre-flight: Create a worktree

Before touching any code, run:
```bash
git worktree add .worktrees/lint-rules -b feat/featured-lint-rules
cd .worktrees/lint-rules
```
All subsequent work happens inside `.worktrees/lint-rules/`.

---

## File Map

| Action | Path | Responsibility |
|---|---|---|
| Modify | `gradle/libs.versions.toml` | Add `lint` version + `lint.api` / `lint.tests` library aliases |
| Modify | `settings.gradle.kts` | Register `:featured-lint-rules` module |
| Create | `featured-lint-rules/build.gradle.kts` | Module build config |
| Create | `featured-lint-rules/src/main/kotlin/dev/androidbroadcast/featured/lint/FeaturedIssueRegistry.kt` | `IssueRegistry` — declares vendor, api, minApi, issue list |
| Create | `featured-lint-rules/src/main/kotlin/dev/androidbroadcast/featured/lint/HardcodedFlagValueDetector.kt` | Detector + `ISSUE` companion |
| Create | `featured-lint-rules/src/test/kotlin/dev/androidbroadcast/featured/lint/HardcodedFlagValueDetectorTest.kt` | Full test suite |

---

## Task 1: Add `lint` to the version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add version and library entries**

Open `gradle/libs.versions.toml`. In the `[versions]` block, add:
```toml
lint = "32.1.0"
```
> Formula: `lint_major = agp_major + 23`. AGP is `9.1.0` → lint is `32.1.0`.
> If AGP patch ever bumps (e.g. 9.2.0), lint must move to 32.2.0 in lockstep.

In the `[libraries]` block, add:
```toml
lint-api = { module = "com.android.tools.lint:lint-api", version.ref = "lint" }
lint-tests = { module = "com.android.tools.lint:lint-tests", version.ref = "lint" }
```

- [ ] **Step 2: Verify the catalog parses**

```bash
./gradlew help --quiet
```
Expected: no error about unresolved version refs.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: add lint-api and lint-tests to version catalog"
```

---

## Task 2: Scaffold the module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `featured-lint-rules/build.gradle.kts`

- [ ] **Step 1: Register module in settings**

In `settings.gradle.kts`, append after the last `include(...)` line:
```kotlin
include(":featured-lint-rules")
```

- [ ] **Step 2: Create `build.gradle.kts`**

Create `featured-lint-rules/build.gradle.kts` with:
```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.bcv)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.kotlin.testJunit)
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "dev.androidbroadcast.featured.lint.FeaturedIssueRegistry")
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = "dev.androidbroadcast.featured",
        artifactId = "featured-lint-rules",
    )
    pom {
        name.set("Featured Lint Rules")
        description.set("Custom Android Lint rules for Featured – enforce correct feature flag usage")
        inceptionYear.set("2024")
        url.set("https://github.com/AndroidBroadcast/Featured")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("androidbroadcast")
                name.set("Kirill Rozov")
                url.set("https://github.com/androidbroadcast")
            }
        }
        scm {
            url.set("https://github.com/AndroidBroadcast/Featured")
            connection.set("scm:git:git://github.com/AndroidBroadcast/Featured.git")
            developerConnection.set("scm:git:ssh://git@github.com/AndroidBroadcast/Featured.git")
        }
    }
}
```

- [ ] **Step 3: Verify module is recognized**

```bash
./gradlew :featured-lint-rules:help --quiet
```
Expected: task runs without "Project ':featured-lint-rules' not found".

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts featured-lint-rules/build.gradle.kts
git commit -m "chore: scaffold :featured-lint-rules module"
```

---

## Task 3: Write the failing tests

**Files:**
- Create: `featured-lint-rules/src/test/kotlin/dev/androidbroadcast/featured/lint/HardcodedFlagValueDetectorTest.kt`

The `lint-tests` library provides `LintDetectorTest` as the base class. You override `getDetector()` and `getIssues()`, then call `lint().files(...).run().expect(...)`.

**Key constraint:** the type resolver needs a `ConfigParam` stub on the classpath. Without it, `evaluator.extendsClass` cannot resolve the type and the detector fires nothing. Every test that should produce a warning must include `configParamStub` in its `.files(...)` list.

- [ ] **Step 1: Create the test file**

```kotlin
package dev.androidbroadcast.featured.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class HardcodedFlagValueDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = HardcodedFlagValueDetector()

    override fun getIssues(): List<Issue> = listOf(HardcodedFlagValueDetector.ISSUE)

    // Minimal stub — primary constructor of the real ConfigParam is internal,
    // so we provide a simplified version with matching val defaultValue: T.
    // T : Any matches the real non-nullable upper bound.
    private val configParamStub = kotlin(
        """
        package dev.androidbroadcast.featured
        class ConfigParam<T : Any>(val key: String, val defaultValue: T)
        """,
    ).indented()

    @Test
    fun `reports defaultValue access on ConfigParam parameter`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    fun check(param: ConfigParam<Boolean>) {
                        if (param.defaultValue) println("on")
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports defaultValue access on ConfigParam local variable`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    fun check() {
                        val flag = ConfigParam("flag", false)
                        println(flag.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `reports defaultValue on chained receiver`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    class Flags {
                        val darkMode = ConfigParam("dark_mode", false)
                    }

                    fun check(flags: Flags) {
                        println(flags.darkMode.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report defaultValue on String receiver`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    fun check(s: String) {
                        println(s.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `does not report access to other ConfigParam properties`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    fun check(param: ConfigParam<Boolean>) {
                        println(param.key)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `does not report correct usage via ConfigValues`() {
        lint()
            .files(
                configParamStub,
                kotlin(
                    """
                    import dev.androidbroadcast.featured.ConfigParam

                    class ConfigValues {
                        operator fun <T : Any> get(param: ConfigParam<T>): T = param.defaultValue
                    }

                    fun check(configValues: ConfigValues, flag: ConfigParam<Boolean>) {
                        val enabled = configValues[flag]
                        println(enabled)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    @Test
    fun `does not report when no ConfigParam stub on classpath`() {
        // Sanity: without the stub, the type is unresolvable — detector stays silent.
        lint()
            .files(
                kotlin(
                    """
                    fun check(x: Any) {
                        println(x.defaultValue)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
```

- [ ] **Step 2: Run tests — verify they fail to compile (detector not yet created)**

```bash
./gradlew :featured-lint-rules:test 2>&1 | tail -20
```
Expected: compilation error — `HardcodedFlagValueDetector` not found.

- [ ] **Step 3: Commit the failing tests**

```bash
git add featured-lint-rules/src/test/
git commit -m "test: add failing tests for HardcodedFlagValueDetector"
```

---

## Task 4: Implement `FeaturedIssueRegistry` (minimal — enough to compile)

**Files:**
- Create: `featured-lint-rules/src/main/kotlin/dev/androidbroadcast/featured/lint/FeaturedIssueRegistry.kt`

The registry must exist before the detector, because `lint-tests` loads the registry to find issues. We'll reference `HardcodedFlagValueDetector.ISSUE` which we create in Task 5.

- [ ] **Step 1: Create the registry**

```kotlin
package dev.androidbroadcast.featured.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

public class FeaturedIssueRegistry : IssueRegistry() {

    override val issues = listOf(HardcodedFlagValueDetector.ISSUE)

    override val api: Int = CURRENT_API

    // minApi = 10 allows AGP consumers on older lint hosts to load the registry.
    // Setting it to CURRENT_API would silently drop all rules for older hosts.
    override val minApi: Int = 10

    override val vendor: Vendor = Vendor(
        vendorName = "Featured",
        feedbackUrl = "https://github.com/AndroidBroadcast/Featured/issues",
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add featured-lint-rules/src/main/kotlin/dev/androidbroadcast/featured/lint/FeaturedIssueRegistry.kt
git commit -m "chore: add FeaturedIssueRegistry scaffold"
```

---

## Task 5: Implement `HardcodedFlagValueDetector`

**Files:**
- Create: `featured-lint-rules/src/main/kotlin/dev/androidbroadcast/featured/lint/HardcodedFlagValueDetector.kt`

**Key UAST facts:**
- In Kotlin, `param.defaultValue` is a **property access**, not a function call. UAST models it as `USimpleNameReferenceExpression` inside a `UQualifiedReferenceExpression`.
- `UCallExpression` would only fire for Java-style `getDefaultValue()` — not what we want.
- `evaluator.extendsClass` takes a `PsiClass`, not a `PsiType`. You must unwrap: `(type as? PsiClassType)?.resolve()`.

- [ ] **Step 1: Create the detector**

```kotlin
package dev.androidbroadcast.featured.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

public class HardcodedFlagValueDetector : Detector(), Detector.UastScanner {

    public companion object {
        public val ISSUE: Issue = Issue.create(
            id = "HardcodedFlagValue",
            briefDescription = "Accessing `ConfigParam.defaultValue` directly bypasses providers",
            explanation = """
                Accessing `defaultValue` directly bypasses any local or remote provider \
                overrides, making the flag effectively hardcoded. \
                Use `ConfigValues` to read the live value instead.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                HardcodedFlagValueDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        private const val CONFIG_PARAM_FQN = "dev.androidbroadcast.featured.ConfigParam"
        private const val DEFAULT_VALUE_PROPERTY = "defaultValue"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                // Only care about references named "defaultValue"
                if (node.identifier != DEFAULT_VALUE_PROPERTY) return

                // Must be the selector of a qualified expression: receiver.defaultValue
                val parent = node.uastParent as? UQualifiedReferenceExpression ?: return

                // Resolve the receiver's type
                val receiverType = parent.receiver.getExpressionType() as? PsiClassType ?: return
                val receiverClass = receiverType.resolve() ?: return

                // Fire only when the receiver is ConfigParam or a subclass
                if (!context.evaluator.extendsClass(receiverClass, CONFIG_PARAM_FQN, false)) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "Accessing `defaultValue` directly on a `ConfigParam` bypasses " +
                        "provider overrides. Use `ConfigValues` to read the live value instead.",
                )
            }
        }
}
```

- [ ] **Step 2: Run the tests**

```bash
./gradlew :featured-lint-rules:test
```
Expected: all 6 tests pass, BUILD SUCCESSFUL.

If tests fail:
- "expectWarningCount(1) but got 0" → type resolution failed; verify `configParamStub` is included in the failing test's `.files(...)`.
- "expectClean() but got 1 warning" → check that the receiver type is correctly unwrapped before calling `extendsClass`.

- [ ] **Step 3: Commit**

```bash
git add featured-lint-rules/src/main/
git commit -m "feat: implement HardcodedFlagValueDetector with UAST type resolution"
```

---

## Task 6: Verify BCV API dump and full build

Binary Compatibility Validator (`bcv`) tracks the public API surface. This module's public API is `HardcodedFlagValueDetector`, `HardcodedFlagValueDetector.ISSUE`, and `FeaturedIssueRegistry`.

- [ ] **Step 1: Generate the API dump**

```bash
./gradlew :featured-lint-rules:apiDump
```
Expected: creates `featured-lint-rules/api/featured-lint-rules.api`.

- [ ] **Step 2: Check the generated dump looks correct**

```bash
cat featured-lint-rules/api/featured-lint-rules.api
```
Expected: contains `HardcodedFlagValueDetector` and `FeaturedIssueRegistry` class entries.

- [ ] **Step 3: Run the full module check**

```bash
./gradlew :featured-lint-rules:check
```
Expected: tests pass, API check passes, spotless passes.

If spotless fails: run `./gradlew :featured-lint-rules:spotlessApply` then re-run `:check`.

- [ ] **Step 4: Commit the API dump**

```bash
git add featured-lint-rules/api/
git commit -m "chore: add BCV API dump for featured-lint-rules"
```

---

## Task 7: Final integration smoke test

Verify the JAR manifest is written correctly — this is what Lint uses to discover the registry.

- [ ] **Step 1: Build the JAR and inspect the manifest**

```bash
./gradlew :featured-lint-rules:jar
unzip -p featured-lint-rules/build/libs/featured-lint-rules-*.jar META-INF/MANIFEST.MF
```
Expected output contains:
```
Lint-Registry-v2: dev.androidbroadcast.featured.lint.FeaturedIssueRegistry
```

- [ ] **Step 2: Final commit**

```bash
git add featured-lint-rules/
git commit -m "feat: add :featured-lint-rules module with HardcodedFlagValue Lint check"
```

---

## Done

At this point `:featured-lint-rules` is a working, tested, BCV-tracked Gradle module ready for publication. The next steps (porting remaining rules, adding `LintFix` suggestions) are tracked as out-of-scope in the design spec.
