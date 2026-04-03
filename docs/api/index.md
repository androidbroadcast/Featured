# API Reference

The full KDoc-generated API reference is built by [Dokka](https://kotlinlang.org/docs/dokka-introduction.html) and published automatically to GitHub Pages on every release.

**[Browse the API reference →](https://androidbroadcast.github.io/Featured/api/)**

---

## Core types

### `ConfigParam<T>`

Declares a named, typed configuration key with a default value.

```kotlin
ConfigParam<T>(
    key: String,
    defaultValue: T,
    description: String = "",
    category: String = "",
)
```

### `ConfigValue<T>`

Wraps a `ConfigParam` with its resolved value and the source that provided it.

```kotlin
data class ConfigValue<T : Any>(
    val param: ConfigParam<T>,
    val value: T,
    val source: Source,
) {
    enum class Source { DEFAULT, LOCAL, REMOTE }
}
```

### `ConfigValues`

Container that composes local and remote providers and exposes flag values reactively.

```kotlin
ConfigValues(
    localProvider: LocalConfigValueProvider? = null,
    remoteProvider: RemoteConfigValueProvider? = null,
)
```

At least one provider must be non-null (enforced at construction time).

**Key methods:**

| Method | Description |
|--------|-------------|
| `getValue(param)` | Suspend: resolve current value |
| `observe(param)` | `Flow<ConfigValue<T>>` — emits on every change |
| `observeValue(param)` | `Flow<T>` — emits raw values only |
| `asStateFlow(param, scope)` | Convert to `StateFlow<T>` |
| `override(param, value)` | Write a local override |
| `resetOverride(param)` | Remove local override |
| `fetch()` | Trigger remote provider fetch and activate |

### `LocalConfigValueProvider`

Interface for writable, observable local storage.

```kotlin
interface LocalConfigValueProvider {
    suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T>?
    fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>?>
    suspend fun <T : Any> setValue(param: ConfigParam<T>, value: T)
    suspend fun <T : Any> removeValue(param: ConfigParam<T>)
}
```

### `RemoteConfigValueProvider`

Interface for fetch-based remote configuration.

```kotlin
interface RemoteConfigValueProvider {
    suspend fun fetch()
    suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T>?
    fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>?>
}
```

---

## Gradle DSL

Flags are declared in `build.gradle.kts` using the `featured { }` extension block provided by the `dev.androidbroadcast.featured` Gradle plugin.

```kotlin title="build.gradle.kts"
featured {
    localFlags {
        boolean("dark_mode", default = false) { category = "UI"; expiresAt = "2026-06-01" }
        int("max_retries", default = 3)
    }
    remoteFlags {
        boolean("promo_banner", default = false) { description = "Show promo banner" }
        string("api_url", default = "https://api.example.com")
    }
}
```

### Generated types

The plugin generates:

| Generated type | Description |
|---|---|
| `internal object GeneratedLocalFlags` | Typed `ConfigParam` properties for every local flag |
| `internal object GeneratedRemoteFlags` | Typed `ConfigParam` properties for every remote flag |
| Extension functions on `ConfigValues` | Local boolean flag → `fun ConfigValues.is<Name>Enabled(): Boolean`; local non-boolean → `fun ConfigValues.get<Name>(): T`; remote → `fun ConfigValues.get<Name>(): ConfigValue<T>` |

### Key tasks

| Task | Description |
|---|---|
| `resolveFeatureFlags` | Resolves DSL-declared flags; runs before all code-generation tasks |
| `generateConfigParam` | Generates `GeneratedLocalFlags` and `GeneratedRemoteFlags` objects |
| `generateFlagRegistrar` | Generates flag registrar for the debug UI |
| `generateProguardRules` | Generates per-function R8 `-assumevalues` rules for local boolean flags |
| `generateIosConstVal` | Generates `expect`/`actual const val` for local flags (iOS) |
| `generateXcconfig` | Generates xcconfig with `DISABLE_*` conditions for local boolean flags |
| `scanAllLocalFlags` | Aggregator task — collects flags across all modules |

---

## Compose extensions (featured-compose)

### `ConfigValues.collectAsState`

```kotlin
@Composable
fun <T : Any> ConfigValues.collectAsState(param: ConfigParam<T>): State<T>
```

Collects the current and future values of `param` as Compose `State`.

### `LocalConfigValues`

```kotlin
val LocalConfigValues: ProvidableCompositionLocal<ConfigValues>
```

Composition local for providing a `ConfigValues` instance through the composition tree.

---

The generated Dokka HTML output lives at `build/dokka/htmlMultiModule/` and is deployed to the `api/` path on GitHub Pages.
