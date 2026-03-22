# JVM Guide

Featured works on plain JVM targets (server, desktop, CLI) without any Android or iOS dependencies.

## Installation

Add only the `core` artifact — no Android or platform-specific modules are needed for JVM:

```kotlin title="build.gradle.kts"
plugins {
    id("dev.androidbroadcast.featured") version "<version>"
}

dependencies {
    implementation(platform("dev.androidbroadcast.featured:featured-bom:<version>"))
    implementation("dev.androidbroadcast.featured:core")
}
```

## Declaring flags

Flags are declared identically to any other platform — `ConfigParam` is a common (KMP shared) type:

```kotlin
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.LocalFlag

object FeatureFlags {
    @LocalFlag
    val darkMode = ConfigParam<Boolean>(
        key = "dark_mode",
        defaultValue = false,
        description = "Enable dark mode UI",
    )

    @LocalFlag
    val pageSize = ConfigParam<Int>(
        key = "page_size",
        defaultValue = 20,
        description = "Number of items per page",
    )
}
```

## Creating `ConfigValues`

On JVM, use `InMemoryConfigValueProvider` (built-in, no external dependencies) or write a custom `LocalConfigValueProvider`:

```kotlin
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.InMemoryConfigValueProvider

val configValues = ConfigValues(
    localProvider = InMemoryConfigValueProvider(),
)
```

## Reading flags

```kotlin
import kotlinx.coroutines.runBlocking

// One-shot read (from a coroutine or runBlocking in tests/scripts)
val value = runBlocking { configValues.getValue(FeatureFlags.darkMode) }
println("dark_mode = ${value.value} (source: ${value.source})")
```

## Reactive observation

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

## Overriding at runtime

```kotlin
// Apply a local override
configValues.override(FeatureFlags.darkMode, true)

// Reset to default
configValues.resetOverride(FeatureFlags.darkMode)
```

## Writing a custom provider

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

    override suspend fun <T : Any> getValue(param: ConfigParam<T>): ConfigValue<T>? {
        val raw = props.getProperty(param.key) ?: return null
        @Suppress("UNCHECKED_CAST")
        val value = raw as? T ?: return null
        return ConfigValue(param, value, ConfigValue.Source.LOCAL)
    }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>?> =
        MutableStateFlow(null) // simplified — add file watching for full reactivity

    override suspend fun <T : Any> setValue(param: ConfigParam<T>, value: T) {
        props.setProperty(param.key, value.toString())
        props.store(file.writer(), null)
    }

    override suspend fun <T : Any> removeValue(param: ConfigParam<T>) {
        props.remove(param.key)
        props.store(file.writer(), null)
    }
}
```

## Next steps

- [Providers](providers.md) — all built-in providers in detail
- [Best practices](best-practices.md) — multi-module setup and testing
