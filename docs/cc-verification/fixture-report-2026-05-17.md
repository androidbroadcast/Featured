# AC-3 — Manual Configuration Cache verification (test fixture)

Date: 2026-05-17
Spec: `docs/specs/2026-05-16-gradle-plugin-cc-support.md` (AC-3)
Target: `featured-gradle-plugin/src/test/fixtures/android-project/`

---

## Versions

- **Gradle**: 9.4.1 (standalone, Homebrew `gradle` — output of `gradle --version`).
  The spec asks for Gradle 9.1; this machine ships 9.4.1 in the 9.x line. AGP 9.1.0 requires
  Gradle 9.0 minimum and is forward-compatible with 9.x — this is the closest available
  standalone Gradle in the 9.x line and matches the AGP 9+ / Gradle 9+ floor stated in the spec.
- **AGP**: 9.1.0 (declared in the fixture `build.gradle.kts`).

## Procedure

The fixture under `featured-gradle-plugin/src/test/fixtures/android-project/` is wired for
TestKit (`GradleRunner.withPluginClasspath()` injects the plugin); a standalone Gradle
invocation has no plugin classpath injection, so the fixture was copied to a scratch
directory and adjusted to resolve the plugin from the local Maven repository.

1. `./gradlew :featured-gradle-plugin:publishToMavenLocal -x signMavenPublication` publishes
   the plugin under coordinates `dev.androidbroadcast.featured:featured-gradle-plugin:0.1.0-SNAPSHOT`
   and the plugin marker `dev.androidbroadcast.featured.gradle.plugin:0.1.0-SNAPSHOT` into `~/.m2`.
2. `cp -r featured-gradle-plugin/src/test/fixtures/android-project /tmp/featured-fixture-ac3`.
3. In `/tmp/featured-fixture-ac3/settings.gradle.kts`, prepend `mavenLocal()` to
   `pluginManagement.repositories` and `dependencyResolutionManagement.repositories`.
4. In `/tmp/featured-fixture-ac3/build.gradle.kts`, change the Featured plugin declaration
   to `id("dev.androidbroadcast.featured") version "0.1.0-SNAPSHOT"`.
5. Add `local.properties` with `sdk.dir=/Users/krozov/dev/android-sdk`.

This isolates the fixture from the parent build (no `includeBuild`, no project wrapper),
satisfying the spec's "exercised in isolation" intent.

## Command

```
cd /tmp/featured-fixture-ac3
gradle assembleRelease --configuration-cache --configuration-cache-problems=fail
```

## Result

```
BUILD SUCCESSFUL in 26s
42 actionable tasks: 42 executed
Configuration cache entry stored.
```

Configuration-cache report:

```
build/reports/configuration-cache/8lulc5p6c95dikv6updqjlgbg/388cxpk2r9ch33znubyiiu37k/configuration-cache-report.html
```

Report header field `"totalProblemCount":0`.

## Total violation count

**0**

## Per-violation table

| Source plugin | Violation category | Upstream issue link |
|---|---|---|
| _none_ | _none_ | _n/a_ |

## Conclusion

AC-3 **passes**. `assembleRelease --configuration-cache --configuration-cache-problems=fail`
completed successfully against the test fixture with zero Configuration Cache violations.
The plugin's six tasks (`resolveFeatureFlags`, `generateProguardRules`,
`generateConfigParam`, `generateFlagRegistrar`, `generateIosConstVal`, `generateXcconfig`)
participated in the build without any CC-attributable issues. The `--problems=fail` flag
would have aborted the build on any single violation; since the build succeeded, no
violations attributable to `featured-gradle-plugin` (or any other source) were emitted.

Raw HTML report is NOT committed per spec Technical Constraints — Markdown summary only.
