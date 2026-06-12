# featured-gradle-plugin

Gradle plugin ID: `dev.androidbroadcast.featured`

Applied to consumer modules via the `featured { }` DSL extension. Flags are declared in
`build.gradle.kts` — not in Kotlin source code. The plugin generates typed `ConfigParam`
objects, `ConfigValues` extension functions, and R8 dead-code-elimination rules.

## DSL

```kotlin
featured {
    generation { // optional module-wide defaults for the generated code
        packageName = "com.example.flags"        // default: dev.androidbroadcast.featured.generated
        visibility = FeaturedVisibility.INTERNAL // default: INTERNAL (objects + extensions)
    }
    localFlags {
        generation { // optional per-section overrides (fall back to the block above)
            className = "MyLocalFlags"           // default: GeneratedLocalFlags<ModuleSuffix>
            packageName = "com.example.local"
            visibility = FeaturedVisibility.PUBLIC
        }
        boolean("dark_mode", default = false) { category = "UI" }
        int("max_retries", default = 3)
        enum("checkout_variant", typeFqn = "com.example.CheckoutVariant", default = "LEGACY")
    }
    remoteFlags {
        boolean("promo_banner", default = false) { description = "Show promo banner" }
    }
}
```

`generation { }` notes:

- A custom `className` replaces the default name entirely (no module suffix) and the output
  file is named after it; JVM-name uniqueness across modules becomes the user's responsibility.
- `className` is rejected in the top-level `featured { generation { } }` block — there are two
  generated objects.
- ProGuard rules and the iOS const-val files follow the **local** section's effective package.
- The iOS expect/actual `const val` declarations stay `public` regardless of `visibility`.

## Tasks registered per module

| Task | Output |
|------|--------|
| `resolveFeatureFlags` | `build/featured/flags.txt` |
| `generateConfigParam` | `build/generated/featured/commonMain/Generated{Local,Remote}Flags.kt` + `Generated{Local,Remote}FlagExtensions.kt` (file names follow custom `className`s) |
| `generateFeaturedProguardRules` | `build/featured/proguard-featured.pro` |
| `generateIosConstVal` | iOS constant value files |
| `generateXcconfig` | `build/featured/FeatureFlags.generated.xcconfig` |

`scanAllLocalFlags` aggregates `resolveFeatureFlags` across all modules.

## Tests

Tests use Gradle TestKit.
