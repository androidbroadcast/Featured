# JVM / Desktop Integration Guide

Featured works on plain JVM targets (server, desktop, CLI) without any Android or iOS dependencies.

## 1. Add Gradle dependencies

```kotlin title="build.gradle.kts"
plugins {
    id("dev.androidbroadcast.featured") version "<version>"
}

dependencies {
    implementation(platform("dev.androidbroadcast.featured:featured-bom:<version>"))

    // Core runtime — always required
    implementation("dev.androidbroadcast.featured:core")

    // Persistent local provider backed by java.util.prefs.Preferences
    implementation("dev.androidbroadcast.featured:javaprefs-provider")

    // Test helper — add to test scope only
    testImplementation("dev.androidbroadcast.featured:featured-testing")
}
```

!!! note
    The `javaprefs-provider` artifact is JVM-only. It does not pull in any Android or Apple platform dependencies.

## 2. Declare flags

Declare flags in `build.gradle.kts` using the `featured { }` DSL block. The plugin generates typed helpers automatically.

```kotlin title="build.gradle.kts"
featured {
    localFlags {
        boolean("dark_mode", default = false) {
            description = "Enable dark mode UI"
        }
        int("page_size", default = 20) {
            description = "Number of items per page"
        }
    }
}
```

The plugin generates `internal object GeneratedLocalFlags` with typed `ConfigParam` properties and public extension functions on `ConfigValues` such as `fun ConfigValues.isDarkModeEnabled(): Boolean` and `fun ConfigValues.getPageSize(): Int`.

## 3. Create `ConfigValues` with `JavaPreferencesConfigValueProvider`

`JavaPreferencesConfigValueProvider` persists values using `java.util.prefs.Preferences`. Storage is OS-specific: the registry on Windows, a plist on macOS, and `~/.java` on Linux. Values survive process restarts automatically.

```kotlin
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.javaprefs.JavaPreferencesConfigValueProvider
import java.util.prefs.Preferences

// Default: stores under the user root, node "featured"
val provider = JavaPreferencesConfigValueProvider()

// Custom node — useful for isolating test data or multiple app instances
val provider = JavaPreferencesConfigValueProvider(
    node = Preferences.userRoot().node("com/example/myapp/flags")
)

val configValues = ConfigValues(localProvider = provider)
```

### Supporting custom types

Built-in support covers `String`, `Int`, `Boolean`, `Float`, `Long`, and `Double`. Register a `TypeConverter` for any additional type before first use:

```kotlin
import dev.androidbroadcast.featured.TypeConverter
import dev.androidbroadcast.featured.javaprefs.registerConverter

enum class Theme { LIGHT, DARK, SYSTEM }

provider.registerConverter<Theme>(
    TypeConverter(
        fromString = { Theme.valueOf(it) },
        toString = { it.name },
    )
)
```

## 4. Initialize and fetch (optional)

On JVM there is typically no remote provider, so `initialize()` and `fetch()` are not required. If you wire a remote provider (e.g., a custom `RemoteConfigValueProvider` backed by a feature-flag service), call them once on startup:

```kotlin
import kotlinx.coroutines.runBlocking

runBlocking {
    configValues.initialize()
    configValues.fetch()
}
```

## 5. Read flags

```kotlin
import kotlinx.coroutines.runBlocking

// One-shot read — from a coroutine or runBlocking in scripts / tests
val value = runBlocking { configValues.getValue(FeatureFlags.darkMode) }
println("dark_mode = ${value.value} (source: ${value.source})")
// source is DEFAULT, LOCAL, or REMOTE
```

## 6. Reactive observation

Featured uses Kotlin Coroutines' `Flow` for reactive updates on all platforms, including JVM:

```kotlin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

runBlocking {
    configValues.observe(FeatureFlags.darkMode).collect { configValue ->
        println("dark_mode changed: ${configValue.value}")
    }
}
```

In a long-lived server process, collect inside a `CoroutineScope` tied to the application lifecycle:

```kotlin
applicationScope.launch {
    configValues.observe(FeatureFlags.darkMode).collect { configValue ->
        // Reconfigure the application when the flag changes
        updateTheme(configValue.value)
    }
}
```

## 7. Override and reset at runtime

```kotlin
// Apply a local override — useful for admin overrides or staged rollouts
configValues.override(FeatureFlags.darkMode, true)

// Reset to the stored or default value
configValues.resetOverride(FeatureFlags.darkMode)

// Clear all local overrides
configValues.clearOverrides()
```

## 8. Testing with `FakeConfigValues`

The `featured-testing` artifact provides `fakeConfigValues` — a suspend factory function that builds a `ConfigValues` backed by an in-memory provider. No real `Preferences` storage is involved.

```kotlin
import dev.androidbroadcast.featured.testing.fakeConfigValues
import dev.androidbroadcast.featured.testing.fake
import kotlinx.coroutines.test.runTest

class FeatureFlagTest {

    @Test
    fun `new checkout is enabled when flag is on`() = runTest {
        val configValues = fakeConfigValues {
            set(FeatureFlags.newCheckout, true)
        }

        val value = configValues.getValue(FeatureFlags.newCheckout)
        assertTrue(value.value)
    }

    @Test
    fun `defaults are used when no override is set`() = runTest {
        val configValues = fakeConfigValues()

        val value = configValues.getValue(FeatureFlags.darkMode)
        assertEquals(FeatureFlags.darkMode.defaultValue, value.value)
    }
}
```

You can also use the companion extension for a more idiomatic call site:

```kotlin
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.testing.fake

val configValues = ConfigValues.fake {
    set(FeatureFlags.pageSize, 50)
}
```

### Simulating mid-test flag changes

`fakeConfigValues` returns a real `ConfigValues` instance — you can call `override` to simulate remote pushes or user-triggered overrides:

```kotlin
@Test
fun `UI updates when flag changes at runtime`() = runTest {
    val configValues = fakeConfigValues {
        set(FeatureFlags.newCheckout, false)
    }

    val collected = mutableListOf<Boolean>()
    val job = launch {
        configValues.observe(FeatureFlags.newCheckout).collect { collected.add(it.value) }
    }

    configValues.override(FeatureFlags.newCheckout, true)
    advanceUntilIdle()

    job.cancel()
    assertEquals(listOf(false, true), collected)
}
```

## 9. Writing a custom provider

Implement `LocalConfigValueProvider` to back flags with any storage (database, config file, etc.):

```kotlin
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class PropertiesConfigValueProvider(
    private val file: java.io.File,
) : LocalConfigValueProvider {

    private val props = java.util.Properties().also {
        if (file.exists()) it.load(file.reader())
    }

    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        val raw = props.getProperty(param.key) ?: return null
        @Suppress("UNCHECKED_CAST")
        val value = raw as? T ?: return null
        return ConfigValue(value, ConfigValue.Source.LOCAL)
    }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
        MutableStateFlow(null) // simplified — add file watching for full reactivity

    override suspend fun <T : Any> set(param: ConfigParam<T>, value: T) {
        props.setProperty(param.key, value.toString())
        props.store(file.writer(), null)
    }

    override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) {
        props.remove(param.key)
        props.store(file.writer(), null)
    }
}
```

## Next steps

- [Providers](providers.md) — all built-in providers in detail
- [Best practices](best-practices.md) — multi-module setup and testing
- [Android guide](android.md) — DataStore, Compose integration, and the debug UI
