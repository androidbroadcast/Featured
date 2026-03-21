# GitHub CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up complete GitHub Actions CI/CD for a KMP library: parallel jobs for tests, Android build, iOS framework + Xcode build, and linting (Spotless/ktlint, Android Lint, SwiftLint), plus security workflows (CodeQL, Dependency Review, Dependabot).

**Architecture:** Four parallel CI jobs run on every PR and push to `main`. Spotless (ktlint engine) and Binary Compatibility Validator (BCV) are added as Gradle plugins. SwiftLint runs in the macOS job alongside the iOS build. CodeQL and Dependency Review run as separate workflows.

**Tech Stack:** GitHub Actions, Spotless 8.3.0 (ktlint 1.8.0 engine), BCV 0.18.1, SwiftLint (Homebrew), CodeQL, Dependabot

---

## Files

**New files:**
- `.github/workflows/ci.yml` — 4 parallel jobs + BCV (continue-on-error)
- `.github/workflows/codeql.yml` — Kotlin static analysis
- `.github/workflows/dependency-review.yml` — CVE scan for new deps in PRs
- `.github/dependabot.yml` — weekly auto-update PRs for Gradle + Actions
- `.swiftlint.yml` — SwiftLint config for iosApp/

**Modified files:**
- `gradle/libs.versions.toml` — add `spotless = "8.3.0"`, `bcv = "0.18.1"` versions and plugins
- `build.gradle.kts` (root) — apply Spotless, configure ktlint for `*.kt` / `*.kts`
- `core/build.gradle.kts` — apply BCV plugin

---

## Task 1: Create worktree

- [ ] Create isolated worktree for this branch:
  ```bash
  git worktree add .worktrees/setup-ci -b chore/setup-ci
  cd .worktrees/setup-ci
  ```
- [ ] Copy `local.properties` if needed (JAVA_HOME):
  ```bash
  cp ../../local.properties . 2>/dev/null || true
  ```

---

## Task 2: Add Spotless and BCV to version catalog

**File:** `gradle/libs.versions.toml`

- [ ] Add versions in `[versions]` section (after `skie`):
  ```toml
  spotless = "8.3.0"
  bcv = "0.18.1"
  ```
- [ ] Add plugins in `[plugins]` section (at end):
  ```toml
  spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
  bcv = { id = "org.jetbrains.kotlinx.binary-compatibility-validator", version.ref = "bcv" }
  ```
- [ ] Verify catalog parses correctly:
  ```bash
  ./gradlew help --task help 2>&1 | grep -E "BUILD|FAILED"
  ```
  Expected: `BUILD SUCCESSFUL`

---

## Task 3: Configure Spotless in root build.gradle.kts

**File:** `build.gradle.kts` (root)

- [ ] Add Spotless plugin and configuration. The root file currently only has `plugins {}`. Add Spotless **with** `apply true` (no `apply false`) and configure it:
  ```kotlin
  plugins {
      // existing entries — keep all of them
      alias(libs.plugins.androidApplication) apply false
      alias(libs.plugins.androidLibrary) apply false
      alias(libs.plugins.composeHotReload) apply false
      alias(libs.plugins.composeMultiplatform) apply false
      alias(libs.plugins.composeCompiler) apply false
      alias(libs.plugins.kotlinMultiplatform) apply false
      alias(libs.plugins.kotlinAndroid) apply false
      alias(libs.plugins.spotless)  // applied to root, no "apply false"
  }

  spotless {
      kotlin {
          target("**/*.kt")
          targetExclude("**/build/**/*.kt")
          ktlint("1.8.0")
      }
      kotlinGradle {
          target("**/*.kts")
          targetExclude("**/build/**/*.kts")
          ktlint("1.8.0")
      }
  }
  ```
- [ ] Verify Spotless tasks are registered:
  ```bash
  ./gradlew tasks --group=spotless 2>&1 | grep -E "spotless|BUILD"
  ```
  Expected: `spotlessCheck`, `spotlessApply` listed

---

## Task 4: Apply BCV to core module

**File:** `core/build.gradle.kts`

- [ ] Add BCV plugin to `plugins {}` block (after `alias(libs.plugins.kover)`):
  ```kotlin
  alias(libs.plugins.bcv)
  ```
- [ ] Verify BCV tasks are registered:
  ```bash
  ./gradlew :core:tasks --group=verification 2>&1 | grep -E "api|BUILD"
  ```
  Expected: `apiCheck`, `apiDump` listed

---

## Task 5: Fix existing formatting with spotlessApply

- [ ] Run auto-fix to apply ktlint formatting to all existing files:
  ```bash
  ./gradlew spotlessApply
  ```
- [ ] Check what files were modified:
  ```bash
  git diff --stat
  ```
- [ ] Run spotlessCheck to confirm no remaining violations:
  ```bash
  ./gradlew spotlessCheck
  ```
  Expected: `BUILD SUCCESSFUL`
- [ ] Commit formatting fixes:
  ```bash
  git add -A
  git commit -m "style: apply ktlint formatting via Spotless"
  ```

---

## Task 6: Generate initial BCV API dump

- [ ] Run apiDump to create the baseline `.api` files:
  ```bash
  ./gradlew :core:apiDump
  ```
- [ ] Verify `.api` file was created:
  ```bash
  ls core/api/
  ```
  Expected: `core.api` (or similar) file present
- [ ] Run apiCheck to confirm baseline passes:
  ```bash
  ./gradlew :core:apiCheck
  ```
  Expected: `BUILD SUCCESSFUL`
- [ ] Commit generated API files and Gradle config changes:
  ```bash
  git add gradle/libs.versions.toml build.gradle.kts core/build.gradle.kts core/api/
  git commit -m "chore: add Spotless, BCV; generate initial API snapshot for core"
  ```

---

## Task 7: Create .swiftlint.yml

**File:** `.swiftlint.yml` (project root)

- [ ] Create SwiftLint config targeting only the `iosApp` Swift sources:
  ```yaml
  disabled_rules:
    - trailing_whitespace

  opt_in_rules:
    - empty_count
    - empty_string

  included:
    - iosApp/iosApp

  excluded:
    - iosApp/iosApp.xcodeproj

  reporter: "xcode"
  ```
- [ ] Commit:
  ```bash
  git add .swiftlint.yml
  git commit -m "chore: add SwiftLint config"
  ```

---

## Task 8: Create .github/workflows directory and Dependabot config

**Files:** `.github/dependabot.yml`, `.github/workflows/` (directory)

- [ ] Create the workflows directory (it does not exist yet):
  ```bash
  mkdir -p .github/workflows
  ```
- [ ] Create `.github/dependabot.yml`:
  ```yaml
  version: 2
  updates:
    - package-ecosystem: "gradle"
      directory: "/"
      schedule:
        interval: "weekly"
        day: "monday"
      labels:
        - "dependencies"

    - package-ecosystem: "github-actions"
      directory: "/"
      schedule:
        interval: "weekly"
        day: "monday"
      labels:
        - "dependencies"
  ```
- [ ] Commit:
  ```bash
  git add .github/dependabot.yml
  git commit -m "chore: add Dependabot for Gradle and GitHub Actions"
  ```

---

## Task 9: Create Dependency Review workflow

**File:** `.github/workflows/dependency-review.yml`

- [ ] Create the workflow (runs only on PRs, scans new deps for CVEs):
  ```yaml
  name: Dependency Review

  on:
    pull_request:
      branches: [ main ]

  permissions:
    contents: read
    pull-requests: write

  jobs:
    dependency-review:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
        - uses: actions/dependency-review-action@v4
  ```
- [ ] Commit:
  ```bash
  git add .github/workflows/dependency-review.yml
  git commit -m "ci: add dependency review CVE scan for PRs"
  ```

---

## Task 10: Create CodeQL workflow

**File:** `.github/workflows/codeql.yml`

- [ ] Create the workflow:
  ```yaml
  name: CodeQL

  on:
    push:
      branches: [ main ]
    pull_request:
      branches: [ main ]
    schedule:
      - cron: "0 0 * * 0"  # Every Sunday at midnight

  permissions:
    actions: read
    contents: read
    security-events: write

  jobs:
    analyze:
      name: Analyze Kotlin
      runs-on: ubuntu-latest

      steps:
        - uses: actions/checkout@v4

        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: 21

        - uses: gradle/actions/setup-gradle@v3

        - uses: github/codeql-action/init@v3
          with:
            languages: java-kotlin

        - uses: github/codeql-action/autobuild@v3

        - uses: github/codeql-action/analyze@v3
  ```
- [ ] Commit:
  ```bash
  git add .github/workflows/codeql.yml
  git commit -m "ci: add CodeQL static analysis for Kotlin"
  ```

---

## Task 11: Create main CI workflow

**File:** `.github/workflows/ci.yml`

This is the main workflow with 5 parallel jobs.

- [ ] Create the workflow:
  ```yaml
  name: CI

  on:
    push:
      branches: [ main ]
    pull_request:
      branches: [ main ]

  concurrency:
    group: ${{ github.workflow }}-${{ github.ref }}
    cancel-in-progress: true

  jobs:

    test:
      name: Tests & Coverage
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4

        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: 21

        - uses: gradle/actions/setup-gradle@v3

        - name: Run tests and verify coverage
          run: ./gradlew test :core:koverVerify

        - uses: actions/upload-artifact@v4
          if: always()
          with:
            name: test-results
            path: |
              **/build/reports/tests/
              **/build/reports/kover/

    build-android:
      name: Build Android
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4

        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: 21

        - uses: gradle/actions/setup-gradle@v3

        - name: Assemble and Lint
          # Exclude :sample to avoid requiring google-services.json for the demo app
          run: ./gradlew assembleDebug lint -x :sample:assembleDebug

        - uses: actions/upload-artifact@v4
          if: always()
          with:
            name: lint-results
            path: "**/build/reports/lint-results-debug.html"

    lint:
      name: Lint (Spotless / ktlint)
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4

        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: 21

        - uses: gradle/actions/setup-gradle@v3

        - name: Check formatting
          run: ./gradlew spotlessCheck

    api-check:
      name: API Compatibility (informational)
      runs-on: ubuntu-latest
      continue-on-error: true  # Non-blocking until first release
      steps:
        - uses: actions/checkout@v4

        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: 21

        - uses: gradle/actions/setup-gradle@v3

        - name: Check public API
          run: ./gradlew apiCheck

    build-ios:
      name: Build iOS
      runs-on: macos-latest
      steps:
        - uses: actions/checkout@v4

        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: 21

        - uses: gradle/actions/setup-gradle@v3

        - name: Install SwiftLint
          run: brew install swiftlint

        - name: Run SwiftLint
          if: always()
          run: swiftlint lint --strict --config .swiftlint.yml

        - name: Link iOS frameworks (all targets)
          # Explicit module prefixes required — root-level task aggregation is not guaranteed
          run: |
            ./gradlew \
              :core:linkDebugFrameworkIosSimulatorArm64 \
              :core:linkDebugFrameworkIosArm64 \
              :core:linkDebugFrameworkIosX64 \
              :datastore-provider:linkDebugFrameworkIosSimulatorArm64 \
              :datastore-provider:linkDebugFrameworkIosArm64 \
              :datastore-provider:linkDebugFrameworkIosX64

        - name: Build Xcode project
          # generic/platform avoids "simulator not found" failures on runners
          # where specific iOS runtime versions are not pre-installed
          run: |
            xcodebuild build \
              -project iosApp/iosApp.xcodeproj \
              -scheme iosApp \
              -destination 'generic/platform=iOS Simulator' \
              CODE_SIGNING_ALLOWED=NO
  ```
- [ ] Commit:
  ```bash
  git add .github/workflows/ci.yml
  git commit -m "ci: add main CI workflow (test, build-android, build-ios, lint, api-check)"
  ```

---

## Task 12: Final verification and PR

- [ ] Verify formatting:
  ```bash
  ./gradlew spotlessCheck
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] Verify BCV baseline:
  ```bash
  ./gradlew :core:apiCheck
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] Verify tests and build:
  ```bash
  ./gradlew test assembleDebug -x :sample:assembleDebug
  ```
  Expected: `BUILD SUCCESSFUL`

- [ ] Validate all YAML files are well-formed:
  ```bash
  python3 -c "
  import yaml, glob, sys
  files = glob.glob('.github/**/*.yml', recursive=True) + ['.github/dependabot.yml', '.swiftlint.yml']
  errors = []
  for f in files:
      try:
          yaml.safe_load(open(f))
          print(f'OK: {f}')
      except Exception as e:
          errors.append(f'ERROR {f}: {e}')
  [print(e) for e in errors]
  sys.exit(len(errors))
  "
  ```
  Expected: all files print `OK`

- [ ] Push and create PR:
  ```bash
  git push -u origin chore/setup-ci
  gh pr create \
    --title "ci: set up GitHub Actions CI/CD" \
    --body "Adds CI workflows, Spotless/ktlint, BCV, SwiftLint, CodeQL, Dependabot"
  ```
