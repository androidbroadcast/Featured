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

Binary Compatibility Validator (BCV) enforces this automatically — a CI check will fail if
a public API surface changes without an explicit `apiDump` update.

## Deprecation Policy

1. An API is marked `@Deprecated` with a `ReplaceWith` suggestion and a `DeprecationLevel.WARNING`.
2. The deprecated API is kept for **at least one minor release** before being promoted to `ERROR` level.
3. APIs at `ERROR` level are removed in the **next major release**.

Example timeline: deprecated in `1.2.0` → error in `1.3.0` → removed in `2.0.0`.

## Releasing a New Version

Releases are fully automated via the [publish workflow](.github/workflows/publish.yml). A release is triggered by pushing a version tag to the repository.

### Steps to Release

1. **Update the version** in `gradle.properties`:
   ```properties
   VERSION_NAME=1.0.0
   ```
   The version must follow [Semantic Versioning](https://semver.org/) (e.g., `1.0.0`, `1.1.0`, `2.0.0-rc1`).

2. **Commit the version bump:**
   ```bash
   git add gradle.properties
   git commit -m "chore: release v1.0.0"
   ```

3. **Create and push a version tag:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   The tag must start with `v` followed by a semver string (e.g., `v1.0.0`, `v1.2.3-rc1`).

### What the Workflow Does

Pushing a `v*` tag triggers the following automated pipeline:

| Step | Description |
|------|-------------|
| **Publish to Maven Central** | Runs on `ubuntu-latest`; signs all artifacts with GPG and publishes them to Maven Central |
| **Build XCFramework** | Runs on `macos-latest`; assembles `FeaturedCore.xcframework.zip` |
| **Create GitHub Release** | Creates a GitHub Release for the tag with auto-generated release notes and attaches the XCFramework zip |
| **Update `Package.swift`** | Computes the XCFramework checksum and commits an updated `Package.swift` back to `main` |

Pushes to `main` (without a tag) publish a `-SNAPSHOT` version to Maven Central for integration testing.

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
- Development snapshots on `main` use the `-SNAPSHOT` suffix (e.g., `0.1.0-SNAPSHOT`)

## Submitting Changes

1. Fork the repository and create a branch from `main`.
2. Make your changes in a focused, single-purpose commit or small series of commits.
3. Ensure all tests pass and `spotlessCheck` is clean.
4. Open a pull request against `main` with a clear description of what changed and why.

## Module Overview

```
featured-compose ──┐
firebase-provider ─┤
datastore-provider ┼──► core
sharedprefs-provider┤
sample ─────────────┘
```

See [README.md](README.md) for a full architecture overview.
