# Providers

`ConfigValues` composes one optional local provider and one optional remote provider. At least one must be provided.

```
ConfigValues
├── LocalConfigValueProvider  (optional, but at least one required)
└── RemoteConfigValueProvider (optional, but at least one required)
```

Remote values take precedence over local values when both are present for the same key.

---

## Built-in local providers

### InMemoryConfigValueProvider

Stores overrides in a plain in-memory `Map`. No setup, no dependencies.

**Use cases:** unit tests, Compose previews, ephemeral runtime overrides that do not need to survive process death.

**Limitations:** values are lost when the process terminates. Not suitable for user-facing feature flag overrides that must persist across app restarts.

```kotlin
val configValues = ConfigValues(
    localProvider = InMemoryConfigValueProvider(),
)
```

Override and reset a value programmatically:

```kotlin
val provider = InMemoryConfigValueProvider()
val configValues = ConfigValues(localProvider = provider)

provider.set(DarkModeParam, true)          // override
provider.resetOverride(DarkModeParam)      // revert to default/remote
provider.clear()                           // remove all overrides (no Flow signal emitted)
```

`set` and `resetOverride` notify active `observe` flows immediately. `clear` does not emit change signals — use `resetOverride` per-param when reactive teardown is needed.

---

### DataStoreConfigValueProvider

Persists overrides to [Jetpack DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore). Reactive: changes emit immediately via `Flow` without polling.

**Supported types natively:** `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`.

**Custom types** (e.g. enums) require a registered `TypeConverter` — see [Custom types](#custom-types) below.

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:datastore-provider")
```

**Setup:**

```kotlin
// Declare once per file, outside any function or class
private val Context.featureFlagsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "feature_flags")

val provider = DataStoreConfigValueProvider(context.featureFlagsDataStore)
val configValues = ConfigValues(localProvider = provider)
```

**Custom type (enum) example:**

```kotlin
enum class CheckoutVariant { STANDARD, ONE_CLICK }

provider.registerConverter(enumConverter<CheckoutVariant>())
```

`registerConverter` must be called before the first `get` or `set` call for that type.

**Persistence behaviour:** writes are performed via `DataStore.edit`, which is atomic and crash-safe. Active `observe` flows re-emit after each write. `clear()` removes all keys from the DataStore file and also causes observers to re-emit.

---

### SharedPreferencesConfigValueProvider

Android-only. Persists overrides to `SharedPreferences`. All reads and writes are dispatched on `Dispatchers.IO`.

**Supported types:** `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`.

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:sharedpreferences-provider")
```

**Setup:**

```kotlin
val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)

val provider = SharedPreferencesProviderConfig(prefs)
val configValues = ConfigValues(localProvider = provider)
```

**Custom type (enum) example:**

```kotlin
provider.registerConverter(enumConverter<CheckoutVariant>())
```

**Additional context:** you can merge an extra `CoroutineContext` into the IO dispatcher used for all operations:

```kotlin
val provider = SharedPreferencesProviderConfig(prefs, additionalContext = myContext)
```

Active `observe` flows receive updates on every `set`, `resetOverride`, or `remove` call for the observed key. Consecutive identical values are deduplicated via `distinctUntilChanged`.

!!! note
    Prefer `DataStoreConfigValueProvider` for new projects. `SharedPreferencesProviderConfig` exists for projects that already rely on `SharedPreferences` and want to avoid a migration.

---

### NSUserDefaultsConfigValueProvider

iOS-only. Persists overrides to [`NSUserDefaults`](https://developer.apple.com/documentation/foundation/nsuserdefaults).

**Supported types:** `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`.

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:nsuserdefaults-provider")
```

**Setup:**

```kotlin
// Uses the standard user defaults
val provider = NSUserDefaultsConfigValueProvider()

// Or use a named suite (recommended for app groups / extensions)
val provider = NSUserDefaultsConfigValueProvider(suiteName = "com.example.app.flags")

val configValues = ConfigValues(localProvider = provider)
```

Active `observe` flows receive updates on every `set` or `resetOverride` call. `clear()` removes all keys but does **not** emit change signals to observers — call `resetOverride` per param when reactive teardown is required.

!!! note
    `NSUserDefaults` returns a default value (0, `false`, `""`) when a key is absent. The provider checks `objectForKey` first to correctly distinguish "not set" from "set to the zero value".

---

### JavaPreferencesConfigValueProvider

JVM-only. Persists overrides using [`java.util.prefs.Preferences`](https://docs.oracle.com/en/java/docs/books/tutorial/essential/environment/prefs.html). Storage is OS-specific: registry on Windows, plist on macOS, `~/.java` on Linux.

**Supported types:** `String`, `Int`, `Long`, `Float`, `Double`, `Boolean`.

**Custom types** require a registered `TypeConverter`.

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:javaprefs-provider")
```

**Setup:**

```kotlin
// Uses the default node "featured" under the user root
val provider = JavaPreferencesConfigValueProvider()

// Or supply a custom Preferences node
val node = Preferences.userRoot().node("com/example/app/flags")
val provider = JavaPreferencesConfigValueProvider(node)

val configValues = ConfigValues(localProvider = provider)
```

**Custom type (enum) example:**

```kotlin
provider.registerConverter(enumConverter<CheckoutVariant>())
```

All I/O is dispatched on `Dispatchers.IO`. Active `observe` flows receive updates on every `set` or `resetOverride` call.

---

## Built-in remote providers

### FirebaseConfigValueProvider

Wraps [Firebase Remote Config](https://firebase.google.com/docs/remote-config). Remote values override local values when present.

**Supported types natively:** `String`, `Boolean`, `Int`, `Long`, `Double`, `Float`.

Enum types are resolved automatically by name — no explicit converter needed. For other custom types, register a `Converter` on the `converters` property:

```kotlin
provider.converters.put<MyEnum>(Converter { MyEnum.fromString(it.asString()) })
```

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:firebase-provider")
```

**Setup:**

```kotlin
val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(dataStore),
    remoteProvider = FirebaseConfigValueProvider(),
)

// Fetch and activate on app start — call from a coroutine
lifecycleScope.launch { configValues.fetch() }
```

Pass a custom `FirebaseRemoteConfig` instance if you manage the Firebase lifecycle yourself:

```kotlin
FirebaseConfigValueProvider(remoteConfig = FirebaseRemoteConfig.getInstance())
```

**Fetch strategy:**

- `configValues.fetch()` calls `fetchAndActivate()` by default — values become immediately available after the call returns.
- Pass `activate = false` to fetch without activating immediately:

```kotlin
configValues.fetch(activate = false)
// activate at the right moment later
configValues.fetch(activate = true)
```

- A `FetchException` is thrown on network errors, timeouts, or service unavailability. Wrap the call in a try/catch and implement exponential backoff for retries.

**Firebase project setup:**

1. Add `google-services.json` (Android) or `GoogleService-Info.plist` (iOS) to your project.
2. In the [Firebase console](https://console.firebase.google.com/), navigate to **Remote Config**.
3. Add parameters whose keys match your `ConfigParam.key` values.
4. Publish the configuration, then call `configValues.fetch()` at app start.

---

## Custom types

All providers that serialize values as strings (`DataStoreConfigValueProvider`, `SharedPreferencesProviderConfig`, `JavaPreferencesConfigValueProvider`) support custom types via `TypeConverter`.

The library ships `enumConverter<T>()` for any enum class:

```kotlin
enum class Theme { LIGHT, DARK, SYSTEM }

provider.registerConverter(enumConverter<Theme>())
```

For non-enum types, implement `TypeConverter<T>` directly:

```kotlin
val uuidConverter = TypeConverter(
    fromString = { UUID.fromString(it) },
    toString = UUID::toString,
)
provider.registerConverter(UUID::class, uuidConverter)
```

Register converters **before** the first `get`, `set`, or `observe` call for the corresponding type.

---

## Writing a custom provider

### Custom local provider

Implement `LocalConfigValueProvider`:

```kotlin
class MyLocalProvider : LocalConfigValueProvider {
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? { … }
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> { … }
    override suspend fun <T : Any> set(param: ConfigParam<T>, value: T) { … }
    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) { … }
    override suspend fun clear() { … }
}
```

### Custom remote provider

Implement `RemoteConfigValueProvider`:

```kotlin
class MyRemoteProvider : RemoteConfigValueProvider {
    override suspend fun fetch(activate: Boolean) { /* fetch from your backend */ }
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? { … }
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> { … }
}
```

---

## Provider composition

`ConfigValues` accepts one local provider and one remote provider:

```kotlin
val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(dataStore),
    remoteProvider = FirebaseConfigValueProvider(),
)
```

Either provider is optional, but at least one must be supplied.

## Provider resolution order

When `ConfigValues.getValue(param)` is called:

1. Check remote provider — return value if present.
2. Check local provider — return value if present.
3. Return `ConfigValue(param, param.defaultValue, Source.DEFAULT)`.

Overrides written via `configValues.override(param, value)` are written to the **local** provider and survive remote fetches.

## Value source

Every `ConfigValue` carries a `source` field indicating where the value came from:

| Source | Meaning |
|---|---|
| `REMOTE` | Fetched from the remote provider |
| `REMOTE_DEFAULT` | Remote provider returned its own default (e.g. Firebase in-app default) |
| `LOCAL` | Written by a local provider override |
| `DEFAULT` | Fell back to `ConfigParam.defaultValue` |
| `UNKNOWN` | Source could not be determined |

Use `source` for debugging or analytics to understand which layer is serving each value.
