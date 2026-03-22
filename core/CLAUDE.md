# core

The main library module. Defines all public abstractions (`ConfigParam`, `ConfigValue`, `ConfigValues`, providers).

## iOS

SKIE is applied here to bridge Kotlin coroutines, sealed classes, and default arguments to Swift. The XCFramework is named `FeaturedCore`. SKIE configuration lives in `skie.toml` at the repo root.

## Coverage

≥90% line coverage is required. Verify with `./gradlew :core:koverVerify`.
