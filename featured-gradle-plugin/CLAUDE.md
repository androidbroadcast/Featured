# featured-gradle-plugin

Gradle plugin ID: `dev.androidbroadcast.featured`

Applied to consumer modules to scan Kotlin sources for `@LocalFlag`-annotated `ConfigParam` declarations and generate derived artifacts. Registers these tasks per module:

| Task | Output |
|------|--------|
| `scanLocalFlags` | `build/featured/local-flags.txt` |
| `generateProguardRules` | `build/featured/proguard-featured.pro` |
| `generateIosConstVal` | iOS constant value file |
| `generateXcconfig` | `.xcconfig` file for iOS builds |

`scanAllLocalFlags` aggregates results across all modules.

Tests use Gradle TestKit.
