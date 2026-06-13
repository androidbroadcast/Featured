# Contributing to Featured

Thank you for your interest in contributing to Featured!

## Development Setup

Clone the repository and open it in Android Studio or IntelliJ IDEA with the Kotlin Multiplatform plugin installed.

```bash
git clone https://github.com/AndroidBroadcast/Featured.git
cd Featured
./gradlew assemble
```

## Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :core:test
./gradlew :sharedpreferences-provider:test
./gradlew :datastore-provider:test

# Run a single test class
./gradlew :core:test --tests "dev.androidbroadcast.featured.ConfigValuesTest"

# Code coverage (core module requires ≥90% line coverage)
./gradlew :core:koverVerify
./gradlew :core:koverHtmlReport

# Android instrumentation tests (requires a connected device or emulator)
./gradlew :core:connectedAndroidTest
```

## Code Style

The project uses Spotless for formatting. Before pushing, verify:

```bash
./gradlew spotlessCheck
# Auto-fix formatting issues
./gradlew spotlessApply
```

All public declarations must have explicit visibility modifiers (explicit API mode is enabled).
Write all code comments in English.

## Versioning Policy

Featured follows [Semantic Versioning](https://semver.org/) (`MAJOR.MINOR.PATCH`):

| Change type | Version component |
|---|---|
| Breaking API change (removed/renamed public symbol, changed signature) | `MAJOR` bump |
| New public API, new module, new provider | `MINOR` bump |
| Bug fix, performance improvement, documentation update | `PATCH` bump |

### What counts as a breaking change

- Removing or renaming a public class, function, property, or module
- Changing a function signature in a way that requires call-site updates
- Changing the behavior of an existing API in a way that requires migration

Public API changes are reviewed manually during code review — there is no automated Binary Compatibility Validator gate. Reviewers verify that any public-surface change is intentional and that the version bump reflects it.

## API Stability and Breaking Changes

Featured has **no deprecation or migration window**. Breaking changes are made directly; the version number reflects the impact per the Versioning table above.

- **Android (Stable):** a breaking public-API change (removed/renamed symbol, changed signature) requires a `MAJOR` version bump.
- **iOS (Preview) and JVM (Preview):** public API may change in `MINOR` releases without a major bump; no migration window is provided.

## Branching Model

| Branch | Role | Merge policy |
|--------|------|--------------|
| `develop` | Integration branch — target of all PRs | Linear history only (squash or rebase merge). Enforced by branch ruleset `required_linear_history`. |
| `main` | Release branch — updated only on releases | Accepts real merge commits (`--no-ff`) pushed directly. No squash restriction. |

| Operation | How |
|-----------|-----|
| Feature / fix PR → `develop` | **Squash merge** (keeps `develop` linear) |
| `develop` → `main` at release time | **Real merge commit** (`git merge --no-ff`), pushed directly to `main` — not via a PR |
| Back-merge `main` → `develop` after release | **Squash-PR** into `develop` (bumps version to next `-SNAPSHOT`, syncs `Package.swift`) |

The real merge on release is intentional: it makes all `develop` commits ancestors of `main`, so history never diverges. Using a squash-PR for `develop` → `main` would collapse those commits and cause `main` to accumulate history that is not in `develop`'s ancestry — requiring periodic manual reconciliation (see issue #226).

## Releasing a New Version

Releases are driven by a four-step process. The [publish workflow](.github/workflows/publish.yml) is triggered by a version tag push.

### Step 1 — Prepare the release on `develop`

Update `gradle.properties` to the release version (remove the `-SNAPSHOT` suffix) and update `CHANGELOG.md`. Merge these changes into `develop` via a normal squash-PR.

```properties
# gradle.properties
VERSION_NAME=1.0.0
```

### Step 2 — Merge `develop` into `main` with a real merge commit

Do **not** use a squash-PR here. A squash would collapse `develop`'s commits and cause `main` to diverge from `develop`'s history, requiring periodic manual reconciliation.

```bash
git checkout main && git pull
git merge --no-ff develop -m "Release v1.0.0"
git push origin main
```

`main` has no branch ruleset restrictions — a direct push of a merge commit is allowed.

### Step 3 — Tag `main` and push the tag

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag must start with `v` followed by a semver string (e.g., `v1.0.0`, `v1.2.3-rc1`). Pushing the tag triggers the automated pipeline described below.

### What the Workflow Does

Pushing a `v*` tag triggers the following automated pipeline:

| Step | Description |
|------|-------------|
| **Publish to Maven Central** | Runs on `ubuntu-latest`; signs all artifacts with GPG and uploads the deployment bundle to Sonatype Central Portal as `USER_MANAGED` — the deployment must be promoted to release manually in the Portal UI |
| **Build XCFramework** | Runs on `macos-latest`; assembles `FeaturedCore.xcframework.zip` |
| **Create GitHub Release** | Creates a GitHub Release for the tag with auto-generated release notes and attaches the XCFramework zip |
| **Update `Package.swift`** | Computes the XCFramework checksum and commits an updated `Package.swift` directly to `main` |

SNAPSHOT versions can be published manually via `workflow_dispatch` on the Actions tab (branch-push SNAPSHOT publishing is disabled — the Central Portal namespace does not have SNAPSHOT deployment enabled).

### Step 4 — Back-merge `main` into `develop`

After the tag is pushed and the workflow has committed the updated `Package.swift` to `main`, open a PR from `main` into `develop`. This PR should:

- Bump `VERSION_NAME` in `gradle.properties` to the next development version (e.g., `1.1.0-SNAPSHOT`).
- Include the `Package.swift` update committed by the workflow bot.

Merge this PR as a **squash merge** to keep `develop` linear.

This back-merge does not introduce history divergence: because Step 2 used a real merge commit, all `develop` commits are already ancestors of `main`. The back-merge PR only carries the version bump and the bot-generated `Package.swift` change — it is not "returning" lost history.

### Required GitHub Secrets

The following secrets must be configured in the repository settings under **Settings → Secrets and variables → Actions**:

| Secret | Description |
|--------|-------------|
| `MAVEN_CENTRAL_USERNAME` | Maven Central (Sonatype) username |
| `MAVEN_CENTRAL_PASSWORD` | Maven Central (Sonatype) password or user token |
| `GPG_SIGNING_KEY` | Armored GPG private key used to sign artifacts |
| `GPG_KEY_ID` | Short key ID of the GPG key (last 8 hex characters) |
| `GPG_KEY_PASSWORD` | Passphrase for the GPG private key |

### Version Naming Convention

- Use [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`
- The tag name must be the version prefixed with `v` (e.g., tag `v1.0.0` → published version `1.0.0`)
- The tag name must match the `VERSION_NAME` in `gradle.properties` (with the `v` prefix stripped)
- Pre-release versions are supported (e.g., `v1.0.0-rc1`, `v1.0.0-beta2`)
- Development snapshots use the `-SNAPSHOT` suffix (e.g., `1.1.0-SNAPSHOT`) and are published manually via `workflow_dispatch`

## Submitting Changes

1. Fork the repository and create a branch from `develop`.
2. Make your changes in a focused, single-purpose commit or small series of commits.
3. Ensure all tests pass and `spotlessCheck` is clean.
4. Open a pull request against `develop` with a clear description of what changed and why.

## Module Overview

```
featured-compose ──┐
firebase-provider ─┤
datastore-provider ┼──► core
sharedprefs-provider┤
sample ─────────────┘
```

See [README.md](README.md) for a full architecture overview.
