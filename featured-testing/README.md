# featured-testing

Test helpers for code that depends on `ConfigValues`.

## Usage

Add as `testImplementation` only — never ship in production.

```kotlin
testImplementation("dev.androidbroadcast.featured:featured-testing")
```

```kotlin
val flags = FakeConfigValues()
flags.set(MyFlags.newCheckout, true)

val sut = MyViewModel(flags)
// assert behaviour when flag is enabled
```

## Key types

- **`FakeConfigValues`** — in-memory `ConfigValues` with preset values
- **`ConfigValuesFakeExtensions`** — convenience `set(param, value)` extensions on `FakeConfigValues`
