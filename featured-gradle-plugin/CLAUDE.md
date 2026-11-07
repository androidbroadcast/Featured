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
        boolean("new_checkout", default = false) {
            defaultFor("debug", true)
        }
        enum("checkout_variant", typeFqn = "com.example.CheckoutVariant", default = "LEGACY")
        boolean("legacy_path", default = true) {
            defaultFor("release", false)
            // discard follows the release effective default, not the base and not the debug override
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

`defaultFor(buildType, value)` sets a different declared default for that Android build type.
Unspecified build types use the base `default`. It is local-only and does not write a stored override.
Product flavors are not a selector. JVM, KMP, and iOS keep the base default.

`discard(...)` notes (per local boolean flag, release effective default `false`):

- Emits an R8 `-checkdiscard class <spec> { *; }` rule that **verifies** the flag-guarded code
  was actually dead-code-eliminated in release builds — the companion check to the `-assumevalues`
  rule that causes the elimination. If anything keeps the code alive (a forgotten DI reference, a
  manifest entry, reflection, an over-broad `-keep`), R8 fails the build with `Discard checks
  failed`; diagnose with `-whyareyoukeeping`.
- Wired like `-assumevalues`: into the app's own R8 for application modules, and as a consumer
  ProGuard file for library modules so the check runs in the consuming app's R8.
- Rejected on non-boolean flags, when the release effective default is `true` (a debug override
  of `false` does not satisfy it), and on remote flags (a runtime-resolved value is never pinned
  at build time). Covers **code only** — resource shrinking is not verified.

## Tasks registered per module

| Task | Output |
|------|--------|
| `resolveFeatureFlags` | `build/featured/flags.txt` |
| `generateConfigParam` | `build/generated/featured/commonMain/` — base default. JVM and KMP compilations read this directory. Android application/library compilations do not. |
| `generateConfigParam<BuildType>` | `build/generated/featured/buildType/<buildType>/` — that build type's effective default. `generateConfigParamDebug` feeds `compileDebugKotlin`; flavors of one build type share the directory. |
| `generateFeaturedProguardRules` | `build/featured/proguard-featured.pro` |
| `generateFeaturedCheckDiscardRules` | `build/featured/proguard-featured-checkdiscard.pro` |
| `generateIosConstVal` | iOS constant value files |
| `generateXcconfig` | `build/featured/FeatureFlags.generated.xcconfig` |

To resolve flags across all modules at once, use Gradle's name-matched task invocation:
`./gradlew resolveFeatureFlags` — Gradle runs the task in every module that applies the plugin.
The plugin holds no `rootProject` access and is compatible with Gradle Project Isolation.

## Auto-wiring generated sources

`generateConfigParam` (this plugin) and `generateFeaturedRegistry` (the
`dev.androidbroadcast.featured.application` aggregator) auto-wire their generated output into the
consumer module's compilation — consumers write **zero** manual `srcDir` / `dependsOn`. They write to
**distinct** directories so the two outputs never overlap when a module applies both plugins:
`generateConfigParam` → `build/generated/featured/commonMain` (JVM and KMP; Android app/library
compilations use `build/generated/featured/buildType/<buildType>` instead), `generateFeaturedRegistry` →
`build/generated/featured/registry`.
The plugin reacts to the applied Kotlin/Android plugin and picks the right source set
(`GeneratedSourceWiring.kt`):

- KMP `org.jetbrains.kotlin.multiplatform` → `commonMain` via `srcDir(Provider)`; Gradle auto-infers the
  task dependency. Covers `com.android.kotlin.multiplatform.library` (it co-requires the KMP plugin).
- Kotlin/JVM `org.jetbrains.kotlin.jvm` → `main` via `srcDir(Provider)`.
- Plain AGP `com.android.application` / `com.android.library` → one `generateConfigParam<BuildType>`
  directory per build type, added with `variant.sources.kotlin.addStaticSourceDirectory`, plus an
  explicit `dependsOn` on that variant's `compile*Kotlin` / `ksp*` / `extract*Annotations` tasks.
  The shared `generateConfigParam` task is not on that compile graph. The aggregator still wires
  `generateFeaturedRegistry` into the AGP `main` source set.

The three branches are mutually exclusive in AGP 9, so exactly one fires per module.

## Tests

Tests use Gradle TestKit.
