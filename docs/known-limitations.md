# Known Limitations

This document tracks behaviour gaps and deferred work that consumers of
`featured-gradle-plugin` and related modules should be aware of. Each entry
links to a tracking issue and the milestone in which it is expected to be
resolved.

## Configuration Cache

`featured-gradle-plugin` officially supports the Gradle Configuration Cache
on Gradle 9.x and AGP 9.x. Verification artefacts:

- `docs/cc-verification/fixture-report-2026-05-16.md` — fixture project audit
- `docs/cc-verification/sample-report-2026-05-16.md` — sample modules audit
- `docs/cc-verification/agp-propagation-check-2026-05-16.md` — AGP provider
  propagation audit (see `AndroidProguardWiring` fallback)

Known upstream gaps observed during verification, if any, are listed in the
sample report under "Per-violation table".

## Isolated projects

`featured-gradle-plugin` is **Configuration-Cache safe** but **not
isolated-projects safe**.

Source: `FeaturedPlugin.kt:157` — `wireToRootAggregator()` calls
`target.rootProject` to lazily register the `scanAllLocalFlags` aggregator on
the root project. Cross-project mutation from a non-root project violates the
isolated-projects contract.

The behaviour is intentional for `1.0.0-Beta`: it lets consumers `apply` the
plugin in any subproject without manual root wiring, which is the dominant
usage pattern today.

**Migration path (v1.1.0):** convert the aggregator wiring into a settings
plugin, or change the contract so consumers register the aggregator once in
the root `build.gradle.kts` and subproject plugins only `dependsOn` it.

Tracking issue:
[androidbroadcast/Featured#186](https://github.com/androidbroadcast/Featured/issues/186)
(milestone `v1.1.0`).

## Third-party plugin CC gaps

Third-party Gradle plugins occasionally introduce Configuration Cache
violations through transitive plugin application. We track such gaps in the
sample audit (`docs/cc-verification/sample-report-2026-05-16.md`) when they
surface. None of these are caused by `featured-gradle-plugin` itself; the
plugin's own task graph is CC-clean per the fixture audit.
