# Best Practices

## Flag lifecycle

Feature flags are temporary by design. Every flag should progress through three stages and then be deleted.

```
Draft → Rollout → Cleanup
```

### 1. Draft — introduce the flag

Declare the flag in `build.gradle.kts` using the `featured { }` DSL block and set an expiry date using `expiresAt` so stale flags surface automatically in CI. Use `localFlags { }` for flags that will be deleted once the rollout is complete, and `remoteFlags { }` for flags that will be permanently controlled from the server.

```kotlin
// GOOD: expiry date set, snake_case key, module-prefixed
featured {
    localFlags {
        boolean("checkout_new_flow", default = false) {
            description = "Enable the redesigned single-page checkout"
            category = "checkout"
            expiresAt = "2026-09-01"
        }
    }
}
```

Guard every entry point — UI composition roots **and** deep-link handlers — behind the generated extension function. Keep the default `false` so the feature is off until you explicitly enable it.

```kotlin
// Guard a Compose entry point
val isNewCheckout by configValues.collectAsState(GeneratedLocalFlags.checkoutNewFlow)

if (isNewCheckout) {
    NewCheckoutScreen()
} else {
    LegacyCheckoutScreen()
}
```

### 2. Rollout — enable remotely

Use a `RemoteConfigValueProvider` (e.g. Firebase Remote Config) to enable the flag for a growing percentage of users. Remote values automatically override local defaults — no code change required.

Declare the flag in `remoteFlags { }` when it is intended to be permanently controlled from the server (A/B experiments, promotional banners). Use `localFlags { }` for flags that will eventually be deleted once the rollout is complete.

```kotlin
// Permanent remote-controlled flag
featured {
    remoteFlags {
        boolean("promo_banner_enabled", default = false) {
            description = "Show a promotional banner (remote-controlled)"
            category = "promotions"
        }
    }
}
```

### 3. Cleanup — delete the flag

Once the feature is fully rolled out and validated:

1. Remove the flag from the `featured { }` DSL block in `build.gradle.kts`.
2. Delete all usages of the generated extension function and any guarding `if` blocks — keep only the new-path code.
3. Remove the corresponding key from your remote configuration backend.
4. Regenerate platform artefacts:

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

```kotlin title=":feature:checkout/build.gradle.kts"
featured {
    localFlags {
        boolean("checkout_new_flow", default = false) {
            description = "Enable the redesigned checkout flow"
            category = "checkout"
            expiresAt = "2026-09-01"
        }
        boolean("checkout_payment_v2", default = false) {
            description = "Enable Payment V2 during checkout"
            category = "checkout"
            expiresAt = "2026-09-01"
        }
    }
}
```

```kotlin title=":feature:promotions/build.gradle.kts"
featured {
    remoteFlags {
        boolean("promo_banner_enabled", default = false) {
            description = "Show a promotional banner (remote-controlled)"
            category = "promotions"
        }
    }
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

    // isCheckoutNewFlowEnabled() is the generated extension function from the DSL declaration
    val isNewFlowEnabled: StateFlow<Boolean> = configValues.asStateFlow(
        param = GeneratedLocalFlags.checkoutNewFlow,
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
// BAD — no expiresAt, will never prompt cleanup
featured {
    localFlags {
        boolean("checkout_new_flow", default = false)
    }
}

// GOOD — expiresAt triggers the ExpiredFeatureFlagRule Detekt warning on the deadline
featured {
    localFlags {
        boolean("checkout_new_flow", default = false) {
            expiresAt = "2026-09-01"
        }
    }
}
```

### Using flags for configuration values

`localFlags` boolean entries are for feature toggles that will be deleted. Long-lived configuration values (thresholds, URLs, strings) should be declared in `remoteFlags { }` — they are not subject to the cleanup lifecycle.

```kotlin
// BAD — a URL is not a temporary feature flag; it will never be "cleaned up"
featured {
    localFlags {
        string("api_base_url", default = "https://api.example.com")
    }
}

// GOOD — permanent remote config value, not a temporary flag
featured {
    remoteFlags {
        string("api_base_url", default = "https://api.example.com")
    }
}
```

### Hardcoding flag values in production code

Hardcoding `true` or `false` instead of reading from `ConfigValues` defeats the purpose of the system. The `HardcodedFlagValueRule` Detekt rule catches direct accesses to `ConfigParam.defaultValue` in production code.

```kotlin
// BAD — bypasses the provider stack entirely
if (FeatureFlags.newCheckout.defaultValue) { ... }

// GOOD — reads the live value through ConfigValues
if (configValues.getValue(FeatureFlags.newCheckout)) { ... }
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
| `ExpiredFeatureFlagRule` | Flags whose `expiresAt` date is in the past |
| `HardcodedFlagValueRule` | Direct access to `ConfigParam.defaultValue` in production code |

With these rules enabled, the lifecycle contract is enforced by CI rather than code review alone.

---

## Security

- Never store secrets (API keys, tokens) as `ConfigParam` values. Flags are for feature toggles and configuration, not credentials.
- Remote Config values are not end-to-end encrypted. Do not use them to gate security-critical behaviour.
- Default values are compiled into the binary. Do not rely on a flag's default being secret.
