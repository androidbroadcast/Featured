# featured-compose

Compose Multiplatform integration for Featured.

## Usage

```kotlin
implementation("dev.androidbroadcast.featured:featured-compose")
```

### Provide `ConfigValues` to the composition tree

```kotlin
CompositionLocalProvider(LocalConfigValues provides configValues) {
    MyApp()
}
```

### Read a flag reactively inside a composable

```kotlin
val flag by rememberConfigValue(MyFlags.newCheckout)
if (flag.value) {
    NewCheckoutScreen()
} else {
    LegacyCheckoutScreen()
}
```

### Previews

Use `FakeConfigValues` from `featured-compose` to supply flag values in `@Preview`:

```kotlin
@Preview
@Composable
fun MyScreenPreview() {
    val fakeFlags = FakeConfigValues(MyFlags.newCheckout to true)
    CompositionLocalProvider(LocalConfigValues provides fakeFlags) {
        MyScreen()
    }
}
```
