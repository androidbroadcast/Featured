---
type: spec
slug: gradle-plugin-cc-support
date: 2026-05-16
status: approved
platform: [generic]
surfaces: [cli]
risk_areas: []
non_functional:
acceptance_criteria_ids: [AC-1, AC-2, AC-3, AC-4, AC-5a, AC-5b, AC-6, AC-7, AC-8]
design:
---

# Spec: featured-gradle-plugin Configuration Cache Compatibility

Date: 2026-05-16
Status: approved
Slug: gradle-plugin-cc-support

---

## Context and Motivation

`featured-gradle-plugin` must support Gradle Configuration Cache (CC) cleanly for consumers running Gradle 9+ and AGP 9+. CC is a baseline expectation for any Gradle plugin in 2026; if our plugin breaks CC, every consumer's build silently degrades or fails. Consumers on CC see meaningfully faster Gradle invocations on cached builds (a flat configuration-time cost is replaced by a near-instant cache load); a plugin that breaks CC forces consumers to either disable CC project-wide (losing the benefit on every module) or stop using the plugin.

Research conducted ahead of this spec (architecture-expert deep dive + codebase inventory + Gradle/AGP 9 best-practice review) established that the plugin is **already structurally CC-compatible**: all six tasks are `@CacheableTask` with `Property`/`Provider`/`RegularFileProperty` inputs and proper `@Input`/`@OutputFile`/`@OutputDirectory` annotations; all `@TaskAction` bodies are `Project`-free; all code generators are pure Kotlin functions; AGP integration uses the documented `androidComponents.onVariants { variant.proguardFiles.add(provider) }` pattern. The five sites that reference `Project` (`extensions.create`, `afterEvaluate`, `plugins.withId`, `rootProject.tasks`, `tasks.configureEach`) all execute at configuration time, which CC allows.

The real gap is **verification and documentation**: no automated test currently asserts CC compatibility (`FeaturedPluginIntegrationTest` runs without `--configuration-cache`), and no consumer-facing documentation states the support level or known upstream limitations. This spec defines the work to verify, prove, and document CC support for v1.0.0-Beta.

**Success metric (outcome).** A consumer enables Configuration Cache (`org.gradle.configuration-cache=true` in `gradle.properties`), applies `featured-gradle-plugin` to their module, and observes (a) zero Configuration Cache report entries attributed to `featured-gradle-plugin` source, and (b) the second build in the same project directory reuses the cache (LOAD, no new hash directory created under `build/reports/configuration-cache/`). The test fixture under `featured-gradle-plugin/src/test/fixtures/android-project/` serves as the canonical consumer surrogate — verification on it is the proxy for verification across consumer projects. AC-1 through AC-8 are the proof artifacts of this outcome; they exist to make the success metric automatically verifiable in CI.

## Acceptance Criteria

The feature is complete when ALL of the following are true.

- [ ] **AC-1** — `FeaturedPluginIntegrationTest` is parametrized over `cc: Boolean`. When `cc=true`, `GradleRunner` invocations include `--configuration-cache --configuration-cache-problems=fail`, and the test runs the build twice in the same project directory. Cache state is asserted via **directory-snapshot of `build/reports/configuration-cache/`**:
  - Before run 1: snapshot the set of top-level subdirectories (`hashSetBefore`).
  - Run 1 (must STORE): assert exactly one new top-level subdirectory appears (`hashSetAfter1 - hashSetBefore == { exactly one new entry }`). This entry is the freshly-stored CC state.
  - Run 2 (must LOAD): assert no new top-level subdirectory appears (`hashSetAfter2 == hashSetAfter1`). Identical-set means Gradle reused the existing CC entry rather than creating a new one.
  - Tests MUST also fail if `build/reports/configuration-cache/` is absent after either run — that indicates CC was not actually enabled by the build at all.
  - Free-text grep of TestKit output (e.g. `"Reusing configuration cache."`) and Configuration Cache HTML report parsing are both explicitly NOT acceptable — the log strings and report internals are not part of Gradle's public API and change across minors. Directory existence and set comparison are observable filesystem state.
- [ ] **AC-2** — Both parametrized scenarios (`cc=false`, `cc=true`) pass in the CI test job. No new CI job is required.
- [ ] **AC-3** — Manual run of `./gradlew assembleRelease --configuration-cache --configuration-cache-problems=fail` against the test fixture under `featured-gradle-plugin/src/test/fixtures/android-project/` completes successfully. The configuration-cache HTML report (located via `find build/reports/configuration-cache -name configuration-cache-report.html`) contains zero violations attributable to `featured-gradle-plugin` source. **Audit artifact** committed at `docs/cc-verification/fixture-report-2026-05-16.md` (Markdown summary only — raw HTML reports MUST NOT be committed) with the following content schema, all sections present:
  - Gradle version used (output of `./gradlew --version`)
  - AGP version used (from fixture `build.gradle.kts`)
  - Exact command invoked
  - Total violation count
  - Per-violation table with columns: `source plugin | violation category | upstream issue link (if any)` (empty table acceptable if zero violations)
  - Conclusion paragraph (one sentence stating whether AC-3 passes and why)
- [ ] **AC-4** — Discovery run of `./gradlew assembleRelease --configuration-cache --configuration-cache-problems=warn` against `:sample:android-app` (and equivalent task on `:sample:shared` and `:sample:desktop`) is performed. Audit artifact committed at `docs/cc-verification/sample-report-2026-05-16.md` using the **same content schema as AC-3** (Gradle/AGP versions, commands, violation count, per-violation table, conclusion). **Pass/fail criterion (explicit):** AC-4 passes if all violations are categorized as third-party (AGP, Compose, Kotlin, Dokka, Kover, vanniktech publish, etc.). AC-4 fails if any violation traces to `featured-gradle-plugin` source — because the plugin is not applied to `:sample:*` modules per scope, a plugin-attributable violation would contradict the architectural assumption and trigger scope re-evaluation, not a fix in this spec.
- [ ] **AC-5a** — AGP variant.proguardFiles propagation verification on AGP 9.1 is performed via the following preflight + experiment, and the methodology + outcome are committed at `docs/cc-verification/agp-propagation-check-2026-05-16.md`:
  - **Preflight 1 (provider exposure):** Read `FeaturedPlugin.kt` task registration site for `GenerateProguardRulesTask` and the call in `AndroidProguardWiring.kt:23-27`. Confirm `proguardTask.flatMap { it.outputFile }` is the form passed to `variant.proguardFiles.add(...)`. If the form is a raw `Provider<RegularFile>` divorced from the task (no `flatMap`/`map` over `TaskProvider`), propagation cannot work; document, abort the experiment, and treat the AC-5b outcome as «keep unchanged». A separate fix to the exposure shape MAY be applied inside this spec (see Affected Modules: `FeaturedPlugin.kt` conditional) only if the change is mechanically trivial (≤ 5-line diff, no semantic change beyond replacing raw provider with `flatMap`); otherwise defer to a follow-up issue.
  - **Preflight 2 (re-validation of Prerequisite #3):** Re-confirm the fixture is still an Android application with R8 actually invoked (`build/outputs/mapping/release/mapping.txt` present after a one-shot test run). This is a re-check, not an independent verification — Prerequisite #3 is the source of truth; preflight 2 catches regressions introduced between prerequisite check and AC-5a execution.
  - **Experiment:** Temporarily remove the `tasks.configureEach` block at `AndroidProguardWiring.kt:30-34`. Run the E2E fixture test with `--configuration-cache --info`. **Assert two things, both required:**
    1. `:generateProguardRules` appears in the executed task graph **before** the R8 task (e.g. `:minifyReleaseWithR8`), via `--info` log inspection or TestKit `BuildResult.tasks`.
    2. **The generated rules are actually consumed by R8.** Use a marker-string technique: temporarily inject a marker into the generated `proguard-rules.pro` (e.g. `-keep class dev.androidbroadcast.featured.${moduleName}.cc_marker_$RANDOM`), then after R8 runs assert the marker appears in R8's merged input — discoverable in `build/intermediates/r8_d8_compat_proguard/release/...` or via `--info` log lines naming R8 input files. Task-graph ordering alone is INSUFFICIENT evidence; both assertions must pass to conclude propagation works.
  - **Outcome recorded:** verification artifact contains preflight findings, experiment commands, observed task graph, R8 input verification result, and conclusion: propagation works / propagation does not work.
- [ ] **AC-5b** — Based on AC-5a outcome:
  - If propagation **works** → `AndroidProguardWiring.kt:30-34` fallback block is **deleted**. Verification: `git log -p AndroidProguardWiring.kt` shows the deletion of lines 30-34 in this spec's commit AND post-commit `grep -n "tasks.configureEach" AndroidProguardWiring.kt` returns no matches. (Source-pattern grep alone is fragile to refactors; the commit-diff anchors the change to this spec.)
  - If propagation **does NOT work** → fallback is **kept unchanged**, and the limitation is documented under AC-6 README section as a known AGP 9.x gap with link to the upstream issue if any. **Narrowing to internal AGP types (e.g., `com.android.build.gradle.internal.tasks.R8Task`) is explicitly FORBIDDEN** — there is no public R8 task type in AGP 9.x, and `internal` packages are excluded from AGP's public API contract.
- [ ] **AC-6** — `README.md` contains a "Configuration cache" section stating: plugin officially supports CC on Gradle 9+ / AGP 9+; lists any third-party upstream limitations discovered during AC-4 (or states "no known upstream limitations at time of release"); if AC-5b kept the fallback, documents the AGP 9.x propagation gap and the workaround. Includes a one-line snippet showing how to enable CC (`org.gradle.configuration-cache=true` in `gradle.properties`).
- [ ] **AC-7** — `CHANGELOG.md` `[1.0.0-Beta]` entry mentions Configuration Cache support in the `### Added` section (single bullet line).
- [ ] **AC-8** — `docs/known-limitations.md` (new file) contains an entry stating that `wireToRootAggregator.rootProject` access in `FeaturedPlugin.kt:157` is CC-safe today but isolated-projects-unsafe. Entry links to an existing GitHub issue tracking this for v1.1.0. **External proof requirement (not self-referential):** `gh issue view <number> --json title,milestone,state` MUST return a non-404 result with `state: OPEN`, `milestone.title: v1.1.0`, and a title beginning with `isolated projects:`. The same `<number>` MUST appear as the linked URL in `docs/known-limitations.md`. Issue creation is part of this AC, not a prerequisite — but the proof is the live issue lookup, not the link the agent wrote into a file it controls.

**Authoritative definition of done.** The implementing agent validates against this list before marking any task complete.

## Prerequisites

| Prerequisite | Status | Owner | Exit criterion |
|---|---|---|---|
| Gradle 9.1, AGP 9.1 pinned | ✅ Done | — | Verified post-#129–#134 migration; `libs.versions.toml` and fixture `build.gradle.kts` reference these versions |
| `featured-shrinker-tests` is Gradle-free | ✅ Done | — | Per #165; pure-JVM R8 harness; CC-orthogonal — no work needed; confirmed via `featured-shrinker-tests/build.gradle.kts` applying only `kotlinJvm` |
| Test fixture is an Android **application** | ⬜ Todo | Agent | Inspect `featured-gradle-plugin/src/test/fixtures/android-project/build.gradle.kts` — MUST apply `com.android.application` plugin and define an `assembleRelease`-bearing variant. If library-only, surface as a blocker BEFORE AC-1; convert the fixture as a sub-step within this spec's scope |
| Test fixture has R8 actually running on release | ⬜ Todo | Agent | The `.kts` fixture MUST set `isMinifyEnabled = true` (Kotlin DSL spelling — not the Groovy `minifyEnabled`) on the release build type. Verify by running once and confirming `build/outputs/mapping/release/mapping.txt` is produced (mapping.txt presence is the canonical signal that R8 ran). `isShrinkResources` is unrelated to R8 invocation and is NOT required by this prerequisite |
| Test framework in `FeaturedPluginIntegrationTest.kt` is identified | ⬜ Todo | Agent | Read `FeaturedPluginIntegrationTest.kt` and its `build.gradle.kts` test dependencies. Pre-decide parametrization path: JUnit 4 → two distinct test methods (`testCcDisabled`, `testCcEnabled`) sharing a private helper (do NOT add `@RunWith(Parameterized.class)` if other runners are in use); JUnit 5 → `@ParameterizedTest` with `@ValueSource(booleans = [false, true])`. Kotest table-driven if and only if Kotest is already on the test classpath. Decision recorded in the implementation PR description. **Do NOT migrate test frameworks** |

## Affected Modules and Files

| Module / File | Change type | Notes |
|---|---|---|
| `featured-gradle-plugin/src/test/kotlin/.../FeaturedPluginIntegrationTest.kt` | Modified | Parametrize over `cc: Boolean`; second-run cache assertion via directory-snapshot of `build/reports/configuration-cache/` per AC-1 |
| `featured-gradle-plugin/src/main/kotlin/.../AndroidProguardWiring.kt` | Modified (conditional) | Per AC-5b: either delete lines 30-34 fallback (if AC-5a propagation works) OR leave unchanged (if propagation broken). No narrowing to internal AGP types. Deletion verified by commit diff |
| `featured-gradle-plugin/src/main/kotlin/.../FeaturedPlugin.kt` | Modified (conditional) | Per AC-5a Preflight 1: only if exposure form is a raw `Provider<RegularFile>` AND the fix is ≤ 5 lines (replace with `proguardTask.flatMap { it.outputFile }`). Otherwise leave unchanged and defer to follow-up issue |
| `featured-gradle-plugin/src/test/fixtures/android-project/build.gradle.kts` | Modified (conditional) | Only if Prerequisites #3/#4 surface a gap — convert to application module / enable R8 |
| `README.md` | Modified | New "Configuration cache" section (AC-6) — support statement, upstream limitations from AC-4, AGP 9.x gap from AC-5b (if applicable), enable snippet |
| `CHANGELOG.md` | Modified | Add CC support line to `[1.0.0-Beta]` → `### Added` (AC-7) |
| `docs/known-limitations.md` | New | Isolated-projects deferral with linked GitHub issue number that passes `gh issue view` lookup (AC-8) |
| `docs/cc-verification/fixture-report-2026-05-16.md` | New | Audit artifact for AC-3 with content schema enforced |
| `docs/cc-verification/sample-report-2026-05-16.md` | New | Audit artifact for AC-4 with content schema enforced |
| `docs/cc-verification/agp-propagation-check-2026-05-16.md` | New | Audit artifact for AC-5a (preflight findings, experiment commands, observed task graph, R8 input verification, outcome conclusion) |

Key integration points:
- TestKit `GradleRunner.withArguments(...)` — conditionally append CC flags
- Directory-snapshot of `build/reports/configuration-cache/` — observable filesystem state, no parsing of Gradle internals required
- `androidComponents.onVariants { variant.proguardFiles.add(provider) }` — already CC-safe, retained as-is

## Technical Approach

**Test parametrization (AC-1, AC-2).** Resolve the framework per Prerequisite #5 before writing the test. Pseudocode (JUnit 4 path):

```kotlin
// helper: snapshot top-level subdirs under build/reports/configuration-cache/
private fun ccHashDirs(projectDir: File): Set<String> {
    val root = projectDir.resolve("build/reports/configuration-cache")
    if (!root.exists()) return emptySet()
    return root.listFiles { f -> f.isDirectory }
        ?.map { it.name }
        ?.toSet()
        ?: emptySet()
}

private fun runAssembleRelease(projectDir: File, cc: Boolean): BuildResult {
    val args = buildList {
        add("assembleRelease")
        add("--stacktrace")
        if (cc) {
            add("--configuration-cache")
            add("--configuration-cache-problems=fail")
        }
    }
    return GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(args)
        .withPluginClasspath()
        .build()
}

@Test fun testCcDisabled() {
    runAssembleRelease(projectDir, cc = false)
}

@Test fun testCcEnabled() {
    val before = ccHashDirs(projectDir)

    runAssembleRelease(projectDir, cc = true)
    val afterRun1 = ccHashDirs(projectDir)
    val newRun1 = afterRun1 - before
    assertTrue(newRun1.size == 1,
        "First CC run must STORE — expected one new hash directory under build/reports/configuration-cache/, got: $newRun1 (before=$before, after=$afterRun1)")

    runAssembleRelease(projectDir, cc = true)
    val afterRun2 = ccHashDirs(projectDir)
    assertEquals(afterRun1, afterRun2,
        "Second CC run must LOAD (reuse) — no new hash directory expected. Delta: ${afterRun2 - afterRun1}")
}
```

The directory-snapshot mechanism observes filesystem state Gradle creates regardless of report HTML/JSON internals, and disambiguates run attribution (no walk-the-tree ordering hazards). If Gradle changes the layout in a future minor and the snapshot strategy starts producing false negatives, the implementing agent SHOULD update the helper to a structurally equivalent observation (e.g., snapshot at the nested-hash level) — but MUST NOT fall back to TestKit output string matching.

**Manual fixture verification (AC-3).** Run from the fixture directory with a Gradle 9.1 installation (not the project wrapper, so the fixture is exercised in isolation): `cd featured-gradle-plugin/src/test/fixtures/android-project && gradle assembleRelease --configuration-cache --configuration-cache-problems=fail`. Locate the HTML report via `find . -name configuration-cache-report.html`. Triage violations:
- Attributable to `featured-gradle-plugin` source → fix in this spec.
- Attributable to AGP, Kotlin Gradle Plugin, or other third-party → not in scope; document in the audit artifact.

The audit artifact (`docs/cc-verification/fixture-report-2026-05-16.md`) must follow the schema in AC-3. Do not commit the raw HTML report — it is multi-MB and not human-readable in a code review.

**Manual sample discovery (AC-4).** Same procedure as AC-3 but against `:sample:android-app`, `:sample:shared`, `:sample:desktop`, with `--configuration-cache-problems=warn` so the build completes and the report captures all violations. Same audit schema. AC-4 explicitly tolerates third-party violations (documented) but fails on any plugin-attributable violation per the criterion.

**AGP propagation check (AC-5a/AC-5b).** Procedure:

1. **Preflight 1 — provider exposure shape.** Read `FeaturedPlugin.kt` ProGuard task registration block. The line registering `proguardTask` and the call site `variant.proguardFiles.add(...)` in `AndroidProguardWiring.kt:23-27` must use `proguardTask.flatMap { it.outputFile }`. If they do — proceed. If they use a raw `Provider<RegularFile>` (no `flatMap`/`map` over `TaskProvider`) — propagation is structurally impossible, abort the experiment, set AC-5b outcome to "keep unchanged", and OPTIONALLY apply a ≤ 5-line fix to switch to `flatMap` (see Affected Modules conditional). Document either path in the audit artifact.

2. **Preflight 2 — fixture state re-validation.** Confirm the fixture meets Prerequisites #3 and #4 by running a one-shot `gradle assembleRelease` against it and verifying `build/outputs/mapping/release/mapping.txt` is produced (R8 actually ran). If not, abort and re-open Prerequisite #4 — do not run the experiment against an R8-skipping fixture.

3. **Experiment.** Locally remove `AndroidProguardWiring.kt:30-34` (or comment out). Inject a marker into the generated ProGuard rules — extend `ProguardRulesGenerator` to append a comment `# featured-cc-marker: ${UUID}` (UUID generated per run, captured in test) to the output. Run the fixture E2E with `--configuration-cache --info`. Assert **both**:
   - `:generateProguardRules` appears in the executed task graph BEFORE the R8 task (parse `--info` log or use TestKit `BuildResult.tasks` ordering).
   - The marker UUID appears in R8's merged input — search candidate locations: `build/intermediates/proguard_files/release/*`, `build/intermediates/r8_d8_compat_proguard/release/*`, or grep `--info` log lines that name files R8 read. The implementing agent identifies the actual location during the experiment and records it in the audit artifact.

4. **Outcome.** Both assertions pass → propagation works → AC-5b deletes the fallback. Either assertion fails → propagation broken → AC-5b keeps the fallback unchanged + AC-6 documents the gap.

**Documentation (AC-6, AC-7, AC-8).**
- README "Configuration cache" section: 1-2 paragraphs of support statement + bulleted list of upstream limitations (or "no known upstream limitations at time of release") + one-line `gradle.properties` example. Reference audit artifacts at `docs/cc-verification/*.md` for transparency.
- CHANGELOG: single line under `[1.0.0-Beta]` → `### Added` → `- Configuration Cache support (Gradle 9+, AGP 9+)`. Format consistent with existing entries.
- `docs/known-limitations.md`: structured by topic — Configuration Cache, isolated projects, third-party plugin gaps. Each entry: short statement + link to upstream issue + target Featured version for resolution. The isolated-projects entry MUST link to a GitHub issue passing the `gh issue view` external proof in AC-8 (use `gh issue create --milestone v1.1.0 --title "isolated projects: migrate wireToRootAggregator away from rootProject access"`).

## Technical Constraints

- Plugin supports Gradle 9+ and AGP 9+ for v1.0.0-Beta. Wider compatibility is NOT promised and may be evaluated for v1.0.0 stable based on Beta feedback.
- No new runtime dependencies. Test parametrization MUST use the test framework already present in the module (no migration from JUnit 4 to JUnit 5 just for parametrization).
- Do NOT refactor existing tasks to BuildService or rewrite generators. They are already CC-compatible per architecture-expert review; refactoring would be invented work with non-zero regression risk.
- Do NOT touch `wireToRootAggregator` (`FeaturedPlugin.kt:157`). It is CC-safe today; isolated-projects migration is separate v1.1.0 work.
- **Internal AGP types (e.g., `com.android.build.gradle.internal.tasks.R8Task`) MUST NOT be referenced from `featured-gradle-plugin` source.** AGP packages under `internal` are excluded from AGP's public API contract and break on AGP minor versions. This rules out the "narrow to `tasks.withType<R8Task>`" path as a resolution to AC-5b.
- Cache reuse assertion in AC-1 MUST use the directory-snapshot mechanism. Free-text grep of TestKit output and parsing of Configuration Cache HTML report fields are both FORBIDDEN — neither is a Gradle public API contract.
- Sample modules (`:sample:android-app`, `:sample:shared`, `:sample:desktop`) MUST NOT have `featured-gradle-plugin` applied as part of this spec. Sample-plugin-application is a separate scope decision.
- Audit artifacts MUST be Markdown summaries following the AC-3 schema, not raw HTML reports.
- AC-8 isolated-projects issue MUST exist as a real GitHub issue verified via `gh issue view` lookup, not just a link in a file the agent controls.

## Decisions Made

| Decision | Choice | Rationale |
|---|---|---|
| Approach | Defensive (verify-and-prove) | architecture-expert confirmed plugin already CC-compatible; holistic refactor would be invented work and carry regression risk |
| AGP / Gradle floor | Gradle 9+ / AGP 9+ | User-confirmed; project already pinned post-#129; allows leveraging current variant API |
| Third-party CC issues | Document only, do not fix | User scope decision: our plugin in scope, third-party out of scope |
| `featured-shrinker-tests` | Out of scope | Pure-JVM, R8 invoked programmatically, no Gradle, no CC relevance |
| `wireToRootAggregator` rootProject access | Deferred to v1.1.0 (AC-8) | CC-safe today; isolated-projects is a separate Gradle consistency tier |
| CI gate | Existing test job covers via test parametrization; no new CI job | Minimal CI surface, leverages existing infrastructure |
| `AndroidProguardWiring.kt:30-34` fallback | Verify propagation on AGP 9.1 (AC-5a). If works — delete (AC-5b). If broken — keep unchanged + document (AC-5b + AC-6). **Do NOT narrow to internal `R8Task`** | AGP exposes no public R8 task type; using internal types breaks on AGP minors. Verify-or-leave is the only safe binary |
| Sample modules CC verification | Discovery-only (AC-4, `--problems=warn`), not gate; fail if plugin-attributable violation found | Sample does not apply our plugin; signal is only about third-party upstream limitations |
| Applying plugin to sample modules | Out of scope | Separate concern from CC compatibility; not blocking Beta |
| Test parameterization framework | Match existing test infra (pre-resolved in Prerequisite #5) | Avoid introducing new test dependency or runner conflict |
| Cache reuse assertion mechanism | Directory-snapshot of `build/reports/configuration-cache/` top-level subdirs | TestKit output strings and CC report HTML/JSON internals are not stable Gradle public API; directory existence and set comparison are observable filesystem state |
| Audit artifact format | Markdown summary with enforced schema | Reviewable in code review; raw HTML is multi-MB and not human-readable |
| AC-5 split into 5a (verification) and 5b (result) | Two binary ACs instead of one bundled AC | Separates process audit from result diff so each is independently falsifiable |
| AC-5a experiment requires R8-input verification, not just task-graph order | Marker-string technique + R8 input inspection | Task-graph ordering does not prove R8 consumed the generated rules; could yield green build with silently-dropped rules in production |
| AC-5b deletion proof | Commit-diff check (`git log -p`) + post-commit absence grep | Source-pattern grep alone is fragile to refactor; commit diff anchors the change to this spec |
| AC-8 issue creation proof | External `gh issue view <number>` lookup, not file-controlled URL | A URL the agent wrote into a file it controls is self-referential; live issue lookup is independent |
| Follow-up issue for v1.1.0 isolated projects | Part of AC-8, not a prerequisite | An action item inside the spec is not a precondition for starting it |
| Provider exposure fix in `FeaturedPlugin.kt` | Conditional in-spec fix if ≤ 5 lines and mechanical; otherwise defer | Avoids invented work; but a trivial mechanical fix that unlocks fallback deletion is worth absorbing |

## Out of Scope

- BuildService rewrite or holistic refactor of existing tasks. No violations exist that would justify it.
- `wireToRootAggregator` migration to a settings plugin. Deferred to v1.1.0.
- CC compatibility for Featured's own non-plugin modules (`:core`, `:providers/*`, `:featured-debug-ui`, `:featured-compose`, etc.). These are library artifacts, not Gradle plugins.
- Fixing CC violations in AGP, Dokka, Kover, vanniktech publish, or any other third-party Gradle plugin. Documented only.
- AGP 8.x or Gradle 8.x compatibility. Narrowed to Gradle 9+ / AGP 9+ for v1.0.0-Beta.
- Migration to Gradle isolated projects mode. Future direction.
- Applying `featured-gradle-plugin` to `:sample:*` modules. Separate decision.
- Use of internal AGP types (`com.android.build.gradle.internal.*`) anywhere in the plugin source. Excluded by Technical Constraints.
- Free-text grep of TestKit output or Configuration Cache HTML/JSON report parsing as cache-reuse signal. Excluded by Technical Constraints.
- Committing raw HTML Configuration Cache reports to the repo. Markdown summaries only.
- Consumer-side CC education or guidance (e.g., advice on consumer's own `build.gradle.kts` CC hygiene). README states our plugin's compatibility, not consumer-side patterns.
- Provider exposure refactor beyond a trivial ≤ 5-line `flatMap` fix in `FeaturedPlugin.kt` if Preflight 1 surfaces a structural gap.

## Open Questions

None — spec is complete.

## Future Phases

**v1.0.0 stable (or v1.1.0):** consider broadening AGP/Gradle floor if consumer demand surfaces during Beta. Decision driven by Beta feedback.

**v1.1.0 — isolated-projects support:** migrate `wireToRootAggregator.rootProject` access to a settings plugin or alternative architecture. Verify all plugin entry points under isolated configuration. Specced separately.

**v1.1.0 — project-wide CC verification:** extend automated CC coverage to non-plugin modules (`:core`, `:providers/*`, etc.) if there is a demonstrated consumer need. Specced separately if pursued.

**Post-v1.0.0:** evaluate applying `featured-gradle-plugin` to sample modules as a demo/showcase. Independent of CC scope.
