# featured-detekt-rules

Detekt rule set that enforces Featured annotation contracts at static analysis time.

## Setup

```kotlin
detektPlugins("dev.androidbroadcast.featured:featured-detekt-rules")
```

## Rules

| Rule | What it catches |
|------|----------------|
| `ExpiredFeatureFlag` | `expiresAt` date is in the past — flag should be removed from the DSL |
| `UncheckedFlagAccess` | Flag value read outside a `@BehindFlag`-annotated call site |
| `InvalidFlagReference` | `ConfigParam` accessed directly instead of via the generated extension functions on `ConfigValues` |
| `HardcodedFlagValue` | Boolean/value literal used instead of reading from `ConfigValues` |

## Why this matters

The runtime does not distinguish local flags from remote flags — that contract is enforced here, statically. These rules are what prevents a generated local-flag param from accidentally being wired to a remote provider, and ensures all flag access goes through the generated typed extension functions.
