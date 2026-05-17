# Changelog

All notable changes to Featured are documented here.

For the full release history with diff links, see the
[GitHub Releases page](https://github.com/AndroidBroadcast/Featured/releases).

---

## Unreleased

_Changes on `main` not yet tagged for release._

### Changed
- Renamed the Gradle ProGuard/R8 generation task from `generateProguardRules` to
  `generateFeaturedProguardRules` to avoid task-name clashes with consumer scripts.
  Migration: update any CI/build scripts that invoke `generateProguardRules` to use
  the new name. The old task name is no longer registered. (#190)

---

## Contributing a changelog entry

When opening a pull request, add a brief entry under **Unreleased** describing your change.
Use one of these categories:

- **Added** — new public API or feature
- **Changed** — changes to existing behaviour
- **Deprecated** — soon-to-be-removed features
- **Removed** — removed features or APIs
- **Fixed** — bug fixes
- **Security** — vulnerability fixes

Format:

```markdown
### Added
- `ConfigValues.newMethod(param)` — short description (#PR)
```
