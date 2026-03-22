# Best Practices

## Multi-module setup

In a multi-module project, apply the Gradle plugin to every module that declares `@LocalFlag` annotations:

```kotlin title=":feature:checkout/build.gradle.kts"
plugins {
    id("dev.androidbroadcast.featured")
    // … other plugins
}
```

Run code generation tasks across all modules at once:

```bash
# Scan flags in all modules
./gradlew scanAllLocalFlags

# Generate R8 rules for all Android modules
./gradlew generateProguardRules

# Generate xcconfig across all modules
./gradlew generateXcconfig
```

### Single ConfigValues instance

Declare a single shared `ConfigValues` in your **app module** and inject it into feature modules through dependency injection. Feature modules declare their own `ConfigParam` objects but do not create `ConfigValues` themselves.

```kotlin title=":app/src/main/kotlin/.../AppModule.kt (Hilt example)"
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideConfigValues(
        @ApplicationContext context: Context,
    ): ConfigValues = ConfigValues(
        localProvider = DataStoreConfigValueProvider(context.featureFlagsDataStore),
        remoteProvider = FirebaseConfigValueProvider(),
    )
}
```

```kotlin title=":feature:checkout/src/main/kotlin/.../CheckoutViewModel.kt"
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val configValues: ConfigValues,
) : ViewModel() {

    val isNewCheckoutEnabled: StateFlow<Boolean> = configValues.asStateFlow(
        param = FeatureFlags.newCheckout,
        scope = viewModelScope,
    )
}
```

See [ADR 001](../adr/001-configvalues-multi-module.md) for the rationale behind this design.

## Testing

Use `InMemoryConfigValueProvider` in tests — it requires no dependencies and is fully synchronous:

```kotlin
class CheckoutViewModelTest {

    private val provider = InMemoryConfigValueProvider()
    private val configValues = ConfigValues(localProvider = provider)

    @Test
    fun `new checkout enabled shows new UI`() = runTest {
        // Arrange: override flag for this test
        configValues.override(FeatureFlags.newCheckout, true)

        val vm = CheckoutViewModelTest(configValues)

        // Assert
        assertEquals(true, vm.isNewCheckoutEnabled.value)
    }

    @Test
    fun `new checkout disabled shows legacy UI`() = runTest {
        // No override — default value (false) applies
        val vm = CheckoutViewModelTest(configValues)

        assertEquals(false, vm.isNewCheckoutEnabled.value)
    }
}
```

## Naming flags

- Use `snake_case` keys — they map cleanly to Firebase Remote Config parameter names and the xcconfig `DISABLE_*` conditions.
- Group related flags with a common prefix: `checkout_new_flow`, `checkout_payment_v2`.
- Keep `description` concise but specific enough to understand at a glance in the debug UI.

## Avoiding flag proliferation

- Remove flags for fully rolled-out features. Dead flags accumulate maintenance cost.
- Use `category` on `ConfigParam` to group flags in the debug UI.
- Archive removed flags in a comment block or changelog entry so the key isn't accidentally reused.

## Flag lifecycle

```
Draft → Rollout → Cleanup
```

1. **Draft** — add flag with `defaultValue = false`. Ship behind the flag.
2. **Rollout** — enable remotely for a percentage of users. Monitor metrics.
3. **Cleanup** — once fully rolled out, delete the flag, the `@LocalFlag` annotation, and any guarding `if` blocks. Run `generateProguardRules` / `generateXcconfig` to keep generated files in sync.

## Security

- Never store secrets (API keys, tokens) as `ConfigParam` values. Flags are intended for boolean/numeric/string feature toggles.
- Remote Config values are not end-to-end encrypted. Do not use them to gate security-critical behaviour.
