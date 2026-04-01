# featured-detekt-rules

Detekt rule set that enforces Featured annotation contracts at static analysis time.

## Setup

```kotlin
detektPlugins("dev.androidbroadcast.featured:featured-detekt-rules")
```

## Rules

| Rule | What it catches |
|------|----------------|
| `MissingFlagAnnotation` | `ConfigParam` property without `@LocalFlag` or `@RemoteFlag` |
| `ExpiredFeatureFlag` | `@ExpiresAt` date is in the past — flag should be removed |
| `UncheckedFlagAccess` | Flag value read outside a `@BehindFlag`-annotated call site |
| `InvalidFlagReference` | `@RemoteFlag` param used where only `@LocalFlag` is expected (or vice versa) |
| `HardcodedFlagValue` | Boolean/value literal used instead of reading from `ConfigValues` |

## Why this matters

The runtime does not distinguish local flags from remote flags — that contract is enforced here, statically. These rules are what prevents a `@LocalFlag` param from accidentally being wired to a remote provider.
