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
        boolean("new_checkout", default = false) {
            // assert the guarded code is gone from release binaries (R8 -checkdiscard)
            discard("com.example.checkout.newflow.**")
        }
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

`discard(...)` notes (per local boolean flag, default `false` only):

- Emits an R8 `-checkdiscard class <spec> { *; }` rule that **verifies** the flag-guarded code
  was actually dead-code-eliminated in release builds — the companion check to the `-assumevalues`
  rule that causes the elimination. If anything keeps the code alive (a forgotten DI reference, a
  manifest entry, reflection, an over-broad `-keep`), R8 fails the build with `Discard checks
  failed`; diagnose with `-whyareyoukeeping`.
- Wired like `-assumevalues`: into the app's own R8 for application modules, and as a consumer
  ProGuard file for library modules so the check runs in the consuming app's R8.
- Rejected on non-boolean flags, on `default = true` flags, and on remote flags (a runtime-resolved
  value is never pinned at build time). Covers **code only** — resource shrinking is not verified.

## Tasks registered per module

| Task | Output |
|------|--------|
| `resolveFeatureFlags` | `build/featured/flags.txt` |
| `generateConfigParam` | `build/generated/featured/commonMain/Generated{Local,Remote}Flags.kt` + `Generated{Local,Remote}FlagExtensions.kt` (file names follow custom `className`s) |
| `generateFeaturedProguardRules` | `build/featured/proguard-featured.pro` |
| `generateFeaturedCheckDiscardRules` | `build/featured/proguard-featured-checkdiscard.pro` |
| `generateIosConstVal` | iOS constant value files |
| `generateXcconfig` | `build/featured/FeatureFlags.generated.xcconfig` |

To resolve flags across all modules at once, use Gradle's name-matched task invocation:
`./gradlew resolveFeatureFlags` — Gradle runs the task in every module that applies the plugin.
The plugin holds no `rootProject` access and is compatible with Gradle Project Isolation.

## Tests

Tests use Gradle TestKit.
