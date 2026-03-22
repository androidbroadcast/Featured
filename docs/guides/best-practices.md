# Best Practices

## Flag lifecycle

Feature flags are temporary by design. Every flag should progress through three stages and then be deleted.

```
Draft → Rollout → Cleanup
```

### 1. Draft — introduce the flag

Declare the `ConfigParam` in your module's flags registry. Annotate it with `@LocalFlag` or `@RemoteFlag` (required by the `MissingFlagAnnotationRule` Detekt rule) and set an expiry date using `@ExpiresAt` so stale flags surface automatically in CI.

```kotlin
// GOOD: annotated, expiry date set, snake_case key, module-prefixed
@LocalFlag
@ExpiresAt("2026-09-01")
val newCheckout: ConfigParam<Boolean> = ConfigParam(
    key = "checkout_new_flow",
    defaultValue = false,
    description = "Enable the redesigned single-page checkout",
    category = "checkout",
)
```

Guard every entry point — UI composition roots **and** deep-link handlers — behind the flag value. Keep the default `false` so the feature is off until you explicitly enable it.

```kotlin
// Guard a Compose entry point
val isNewCheckout by configValues.collectAsState(FeatureFlags.newCheckout)

if (isNewCheckout) {
    NewCheckoutScreen()
} else {
    LegacyCheckoutScreen()
}
```

### 2. Rollout — enable remotely

Use a `RemoteConfigValueProvider` (e.g. Firebase Remote Config) to enable the flag for a growing percentage of users. Remote values automatically override local defaults — no code change required.

Annotate the flag `@RemoteFlag` when it is intended to be permanently controlled from the server (A/B experiments, promotional banners). Use `@LocalFlag` for flags that will eventually be deleted once the rollout is complete.

```kotlin
// Permanent remote-controlled flag
@RemoteFlag
val promoBannerEnabled: ConfigParam<Boolean> = ConfigParam(
    key = "promo_banner_enabled",
    defaultValue = false,
    description = "Show a promotional banner (remote-controlled)",
    category = "promotions",
)
```

### 3. Cleanup — delete the flag

Once the feature is fully rolled out and validated:

1. Delete the `ConfigParam` declaration.
2. Remove the `@LocalFlag` / `@ExpiresAt` annotations (they go with the declaration).
3. Remove the guarding `if` blocks — keep only the new-path code.
4. Remove the corresponding key from your remote configuration backend.
5. Regenerate platform artefacts:

```bash
./gradlew generateProguardRules   # keep Android R8 rules in sync
./gradlew generateXcconfig        # keep iOS xcconfig in sync
```

The `ExpiredFeatureFlagRule` Detekt rule will warn at build time for any flag whose `@ExpiresAt` date has passed, preventing flags from silently accumulating.

---

## Naming conventions

| What | Convention | Example |
|---|---|---|
| `ConfigParam` key | `snake_case`, module-prefixed | `checkout_new_flow` |
| Kotlin property | `camelCase` matching the key | `newCheckoutFlow` |
| Firebase / remote key | Same `snake_case` as the key | `checkout_new_flow` |

Group related flags with a shared prefix (`checkout_*`, `payments_*`). This keeps the debug UI readable and makes it obvious which team owns each flag.

```kotlin
// GOOD
val newCheckoutFlow: ConfigParam<Boolean> = ConfigParam(key = "checkout_new_flow", ...)
val checkoutPaymentV2: ConfigParam<Boolean> = ConfigParam(key = "checkout_payment_v2", ...)

// BAD — no module prefix, impossible to attribute ownership
val enabled: ConfigParam<Boolean> = ConfigParam(key = "new_flow", ...)
```

---

## Multi-module patterns

### One FlagRegistry per module

Each feature module declares its own object holding its `ConfigParam` instances. The module does not create `ConfigValues` — that is the app module's responsibility.

```kotlin title=":feature:checkout/src/.../CheckoutFlags.kt"
public object CheckoutFlags {

    @LocalFlag
    @ExpiresAt("2026-09-01")
    public val newFlow: ConfigParam<Boolean> = ConfigParam(
        key = "checkout_new_flow",
        defaultValue = false,
        description = "Enable the redesigned checkout flow",
        category = "checkout",
    )

    @LocalFlag
    @ExpiresAt("2026-09-01")
    public val paymentV2: ConfigParam<Boolean> = ConfigParam(
        key = "checkout_payment_v2",
        defaultValue = false,
        description = "Enable Payment V2 during checkout",
        category = "checkout",
    )
}
```

```kotlin title=":feature:promotions/src/.../PromotionsFlags.kt"
public object PromotionsFlags {

    @RemoteFlag
    public val promoBanner: ConfigParam<Boolean> = ConfigParam(
        key = "promo_banner_enabled",
        defaultValue = false,
        description = "Show a promotional banner (remote-controlled)",
        category = "promotions",
    )
}
```

### App-level aggregation

The app module owns the single `ConfigValues` instance and wires together providers. Feature modules receive `ConfigValues` via dependency injection.

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

Feature modules consume `ConfigValues` without knowing how providers are wired:

```kotlin title=":feature:checkout/src/.../CheckoutViewModel.kt"
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val configValues: ConfigValues,
) : ViewModel() {

    val isNewFlowEnabled: StateFlow<Boolean> = configValues.asStateFlow(
        param = CheckoutFlags.newFlow,
        scope = viewModelScope,
    )
}
```

See [ADR 001](../adr/001-configvalues-multi-module.md) for the rationale behind this design.

---

## Testing

Use `fakeConfigValues` from the `featured-testing` artifact — it is fully synchronous, has no external dependencies, and supports both initial values and mid-test overrides.

```kotlin
import dev.androidbroadcast.featured.testing.fakeConfigValues

class CheckoutViewModelTest {

    @Test
    fun `new checkout flow enabled shows new UI`() = runTest {
        val configValues = fakeConfigValues {
            set(CheckoutFlags.newFlow, true)
        }
        val vm = CheckoutViewModel(configValues)

        assertEquals(true, vm.isNewFlowEnabled.value)
    }

    @Test
    fun `new checkout flow disabled shows legacy UI`() = runTest {
        // No override — default value (false) applies
        val configValues = fakeConfigValues()
        val vm = CheckoutViewModel(configValues)

        assertEquals(false, vm.isNewFlowEnabled.value)
    }

    @Test
    fun `reactive update when flag toggled mid-test`() = runTest {
        val configValues = fakeConfigValues {
            set(CheckoutFlags.newFlow, false)
        }
        val vm = CheckoutViewModel(configValues)

        // Simulate a remote change arriving during the session
        configValues.override(CheckoutFlags.newFlow, true)

        assertEquals(true, vm.isNewFlowEnabled.value)
    }
}
```

Never use real providers (`FirebaseConfigValueProvider`, `DataStoreConfigValueProvider`) in unit tests — they require Android or network context and make tests non-deterministic.

---

## Anti-patterns

### Flags that never get cleaned up

Flags are temporary scaffolding, not permanent configuration. Without an expiry date they accumulate silently.

```kotlin
// BAD — no @ExpiresAt, will never prompt cleanup
@LocalFlag
val newCheckout: ConfigParam<Boolean> = ConfigParam(key = "checkout_new_flow", defaultValue = false)

// GOOD — @ExpiresAt triggers the ExpiredFeatureFlagRule Detekt warning on the deadline
@LocalFlag
@ExpiresAt("2026-09-01")
val newCheckout: ConfigParam<Boolean> = ConfigParam(key = "checkout_new_flow", defaultValue = false)
```

### Using flags for configuration values

`ConfigParam<Boolean>` is for feature toggles that will be deleted. Long-lived configuration values (thresholds, URLs, strings) should use the appropriate type directly and be annotated `@RemoteFlag` — they are not subject to the cleanup lifecycle.

```kotlin
// BAD — a URL is not a feature flag; it will never be "cleaned up"
@LocalFlag
val apiBaseUrl: ConfigParam<String> = ConfigParam(key = "api_base_url", defaultValue = "https://api.example.com")

// GOOD — permanent remote config value, not a temporary flag
@RemoteFlag
val apiBaseUrl: ConfigParam<String> = ConfigParam(key = "api_base_url", defaultValue = "https://api.example.com")
```

### Hardcoding flag values in production code

Hardcoding `true` or `false` instead of reading from `ConfigValues` defeats the purpose of the system. The `HardcodedFlagValueRule` Detekt rule catches direct accesses to `ConfigParam.defaultValue` in production code.

```kotlin
// BAD — bypasses the provider stack entirely
if (FeatureFlags.newCheckout.defaultValue) { ... }

// GOOD — reads the live value through ConfigValues
if (configValues.getValue(FeatureFlags.newCheckout)) { ... }
```

### Missing `@LocalFlag` or `@RemoteFlag` annotation

Every `ConfigParam` property must carry one of these annotations. The `MissingFlagAnnotationRule` Detekt rule enforces this, making intent explicit and enabling the Gradle plugin's code-generation tasks.

```kotlin
// BAD — unannotated, Detekt will warn
val newCheckout: ConfigParam<Boolean> = ConfigParam(key = "checkout_new_flow", defaultValue = false)

// GOOD
@LocalFlag
@ExpiresAt("2026-09-01")
val newCheckout: ConfigParam<Boolean> = ConfigParam(key = "checkout_new_flow", defaultValue = false)
```

### Testing with real providers

```kotlin
// BAD — requires Firebase SDK and network; non-deterministic
val configValues = ConfigValues(remoteProvider = FirebaseConfigValueProvider())

// GOOD — deterministic, no dependencies
val configValues = fakeConfigValues { set(CheckoutFlags.newFlow, true) }
```

---

## Automated enforcement (Detekt rules)

Add the `featured-detekt-rules` dependency to your Detekt configuration to enforce the above patterns automatically at build time:

| Rule | What it catches |
|---|---|
| `MissingFlagAnnotationRule` | `ConfigParam` properties missing `@LocalFlag` or `@RemoteFlag` |
| `ExpiredFeatureFlagRule` | Flags whose `@ExpiresAt` date is in the past |
| `HardcodedFlagValueRule` | Direct access to `ConfigParam.defaultValue` in production code |

With these rules enabled, the lifecycle contract is enforced by CI rather than code review alone.

---

## Security

- Never store secrets (API keys, tokens) as `ConfigParam` values. Flags are for feature toggles and configuration, not credentials.
- Remote Config values are not end-to-end encrypted. Do not use them to gate security-critical behaviour.
- Default values are compiled into the binary. Do not rely on a flag's default being secret.
