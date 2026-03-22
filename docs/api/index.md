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

## Annotations

### `@LocalFlag`

Marks a `ConfigParam` property for scanning by the Gradle plugin. The plugin uses this annotation to generate R8 rules (Android/JVM) and xcconfig conditions (iOS).

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class LocalFlag
```

### `@RemoteFlag`

Marks a `ConfigParam` as remote-only. Remote-only flags are excluded from code-generation tasks.

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class RemoteFlag
```

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
