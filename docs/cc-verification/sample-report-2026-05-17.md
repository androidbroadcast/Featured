# AC-4 — Configuration Cache verification (sample modules)

Date: 2026-05-17
Spec: `docs/specs/2026-05-16-gradle-plugin-cc-support.md` (AC-4)
Targets: `:sample:android-app`, `:sample:desktop`, `:sample:shared`

---

## Versions

- **Gradle**: 9.4.1 (project wrapper, `gradle/wrapper/gradle-wrapper.properties`).
- **AGP**: 9.1.0 (`gradle/libs.versions.toml` `agp = "9.1.0"`).
- **Kotlin / KMP**: as pinned by the project version catalog.

## Procedure

For each target, Configuration Cache was exercised against the worktree
checkout (no isolation needed — sample modules already build inside the main
project graph). The cache directory `.gradle/configuration-cache/` was
preserved between runs; Gradle's "store" / "reuse" semantics emit a fresh
report on every invocation regardless of cache state. Reports were located
under `build/reports/configuration-cache/<hash>/<hash>/configuration-cache-report.html`
and the embedded `totalProblemCount` field was extracted.

Flag set (per AC-4): `--configuration-cache --configuration-cache-problems=warn`.

## Commands

```
./gradlew :sample:android-app:assembleRelease --configuration-cache --configuration-cache-problems=warn
./gradlew :sample:desktop:packageDistributionForCurrentOs --configuration-cache --configuration-cache-problems=warn
./gradlew :sample:shared:assemble --configuration-cache --configuration-cache-problems=warn
```

## Results

| Target | Task | Outcome | totalProblemCount |
|---|---|---|---|
| `:sample:android-app` | `assembleRelease` | BUILD SUCCESSFUL | **0** |
| `:sample:desktop` | `packageDistributionForCurrentOs` | BUILD SUCCESSFUL | **0** |
| `:sample:shared` | `assemble` | BUILD SUCCESSFUL | **0** |

CC report file paths (worktree-local, not committed):

- `build/reports/configuration-cache/1tnvmqh6i49z69q5oxzp0rbok/4d30sp07hqqdwrb14owusq9tc/configuration-cache-report.html`
  — `requestedTasks=":sample:android-app:assembleRelease"`,
  `"totalProblemCount":0`.
- `build/reports/configuration-cache/5siguhmx74e0q75wi8y5s2a2g/bvbsmtgdip3i8zbqzk8kjjbfm/configuration-cache-report.html`
  — `requestedTasks=":sample:desktop:packageDistributionForCurrentOs"`,
  `"totalProblemCount":0`.
- `build/reports/configuration-cache/9w23w7drdktwts3ibccle4xcz/d5f0zuupg6ng7cvwceuevl79l/configuration-cache-report.html`
  — `requestedTasks=":sample:shared:assemble"`,
  `"totalProblemCount":0`.

## Total violation count

**0** across all three sample targets.

## Per-violation table

| Source plugin | Violation category | Upstream issue link |
|---|---|---|
| _none_ | _none_ | _n/a_ |

## Notes on `:sample:shared`

`:sample:shared` is a KMP library (`com.android.kotlin.multiplatform.library`
+ JVM + iOS targets). It does not produce a final Android APK and does not
exercise R8, so the AC-4 pass criterion ("no violation traces to
`featured-gradle-plugin`") is verified via `:sample:shared:assemble`, which
materialises every library output artefact (Android AAR, JVM jar, Kotlin
metadata). The CC report shows zero problems.

## Conclusion

AC-4 **passes**. All three sample targets store and reuse Configuration Cache
entries with zero problems on Gradle 9.4.1 / AGP 9.1.0. No violation traces
to `featured-gradle-plugin` source; no upstream / third-party plugin
violations were emitted on this AGP / Compose Multiplatform / Kotlin
combination. The R8 propagation gap audited under AC-5a is independent of
violation counting — it is a behavioural gap in
`variant.proguardFiles` provider wiring, not a CC violation, so it does not
appear in any of these reports.

Raw HTML reports are NOT committed per spec Technical Constraints — Markdown
summary only.
