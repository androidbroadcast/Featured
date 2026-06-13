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

To resolve flags across all modules at once, use Gradle's name-matched task invocation:
`./gradlew resolveFeatureFlags` — Gradle runs the task in every module that applies the plugin.
The plugin holds no `rootProject` access and is compatible with Gradle Project Isolation.

## Auto-wiring generated sources

`generateConfigParam` (this plugin) and `generateFeaturedRegistry` (the
`dev.androidbroadcast.featured.application` aggregator) auto-wire their `build/generated/featured/commonMain`
output into the consumer module's compilation — consumers write **zero** manual `srcDir` / `dependsOn`.
The plugin reacts to the applied Kotlin/Android plugin and picks the right source set
(`GeneratedSourceWiring.kt`):

- KMP `org.jetbrains.kotlin.multiplatform` → `commonMain` via `srcDir(Provider)`; Gradle auto-infers the
  task dependency. Covers `com.android.kotlin.multiplatform.library` (it co-requires the KMP plugin).
- Kotlin/JVM `org.jetbrains.kotlin.jvm` → `main` via `srcDir(Provider)`.
- Plain AGP `com.android.application` / `com.android.library` → `sourceSets["main"].kotlin.directories.add(<resolved File path>)`
  plus an explicit `dependsOn` on every `compile*Kotlin` / `ksp*` task. AGP's `AndroidSourceDirectorySet`
  rejects a `Provider` at configuration time, so a resolved path is used and ordering is wired by hand.

The three branches are mutually exclusive in AGP 9, so exactly one fires per module.

## Tests

Tests use Gradle TestKit.
