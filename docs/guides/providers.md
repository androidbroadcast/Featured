# Providers

`ConfigValues` composes one optional local provider and one optional remote provider. At least one must be provided.

```
ConfigValues
├── LocalConfigValueProvider  (optional, but at least one required)
└── RemoteConfigValueProvider (optional, but at least one required)
```

Remote values take precedence over local values when both are present for the same key.

## Built-in local providers

### InMemoryConfigValueProvider

No setup required. Values are stored in memory and lost on process restart. Useful for tests and previews.

```kotlin
val configValues = ConfigValues(
    localProvider = InMemoryConfigValueProvider(),
)
```

### DataStoreConfigValueProvider

Persists overrides to Jetpack DataStore Preferences. Reactive: changes emit immediately via `Flow`.

```kotlin
// Declare once per file, outside any function or class
private val Context.featureFlagsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "feature_flags")

val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(context.featureFlagsDataStore),
)
```

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:datastore-provider")
```

### SharedPreferencesConfigValueProvider

Android-only. Persists overrides to `SharedPreferences`.

```kotlin
val prefs = context.getSharedPreferences("feature_flags", Context.MODE_PRIVATE)

val configValues = ConfigValues(
    localProvider = SharedPreferencesConfigValueProvider(prefs),
)
```

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:sharedpreferences-provider")
```

!!! note
    Prefer `DataStoreConfigValueProvider` for new projects. `SharedPreferencesConfigValueProvider` exists for projects that already rely on SharedPreferences.

## Built-in remote providers

### FirebaseConfigValueProvider

Wraps Firebase Remote Config. Remote values override local values when present.

```kotlin
val configValues = ConfigValues(
    localProvider = DataStoreConfigValueProvider(dataStore),
    remoteProvider = FirebaseConfigValueProvider(),
)

// Fetch and activate — call from a coroutine on app start
lifecycleScope.launch { configValues.fetch() }
```

Pass a custom instance if you manage the Firebase lifecycle yourself:

```kotlin
FirebaseConfigValueProvider(remoteConfig = FirebaseRemoteConfig.getInstance())
```

**Dependency:**

```kotlin
implementation("dev.androidbroadcast.featured:firebase-provider")
```

## Writing a custom provider

### Custom local provider

Implement `LocalConfigValueProvider`:

```kotlin
class MyLocalProvider : LocalConfigValueProvider {
    override suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T>? { … }
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>?> { … }
    override suspend fun <T : Any> setValue(param: ConfigParam<T>, value: T) { … }
    override suspend fun <T : Any> removeValue(param: ConfigParam<T>) { … }
}
```

### Custom remote provider

Implement `RemoteConfigValueProvider`:

```kotlin
class MyRemoteProvider : RemoteConfigValueProvider {
    override suspend fun fetch() { /* fetch from your backend */ }
    override suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T>? { … }
    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>?> { … }
}
```

## Provider resolution order

When `ConfigValues.getValue(param)` is called:

1. Check remote provider — return value if present.
2. Check local provider — return value if present.
3. Return `ConfigValue(param, param.defaultValue, Source.DEFAULT)`.

Overrides written via `configValues.override(param, value)` are written to the **local** provider and survive remote fetches.
