# featured-lint-rules

Android Lint rule set that mirrors `featured-detekt-rules` for teams using Lint.

## Setup

```kotlin
lintChecks("dev.androidbroadcast.featured:featured-lint-rules")
```

## Rules

| Detector | Issue |
|----------|-------|
| `HardcodedFlagValueDetector` | Boolean/value literal used instead of reading from `ConfigValues` |

More rules are being added to reach parity with the detekt set.
