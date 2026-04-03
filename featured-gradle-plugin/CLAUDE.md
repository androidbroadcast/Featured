# featured-gradle-plugin

Gradle plugin ID: `dev.androidbroadcast.featured`

Applied to consumer modules via the `featured { }` DSL extension. Flags are declared in
`build.gradle.kts` — not in Kotlin source code. The plugin generates typed `ConfigParam`
objects, `ConfigValues` extension functions, and R8 dead-code-elimination rules.

## DSL

```kotlin
featured {
    localFlags {
        boolean("dark_mode", default = false) { category = "UI" }
        int("max_retries", default = 3)
    }
    remoteFlags {
        boolean("promo_banner", default = false) { description = "Show promo banner" }
    }
}
```

## Tasks registered per module

| Task | Output |
|------|--------|
| `resolveFeatureFlags` | `build/featured/flags.txt` |
| `generateConfigParam` | `build/generated/featured/commonMain/Generated{Local,Remote}Flags.kt` + `GeneratedFlagExtensions.kt` |
| `generateFlagRegistrar` | `build/generated/featured/GeneratedFlagRegistrar.kt` |
| `generateProguardRules` | `build/featured/proguard-featured.pro` |
| `generateIosConstVal` | iOS constant value files |
| `generateXcconfig` | `build/featured/FeatureFlags.generated.xcconfig` |

`scanAllLocalFlags` aggregates `resolveFeatureFlags` across all modules.

## Tests

Tests use Gradle TestKit.
