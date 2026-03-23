package dev.androidbroadcast.featured

/**
 * Marks a function, class, or property that must only be used inside a valid feature-flag
 * context — i.e., where the named flag has been checked before execution.
 *
 * ## Intended workflow
 *
 * 1. **Declare the flag** — introduce a `ConfigParam` annotated with `@LocalFlag` or
 *    `@RemoteFlag`. The `flagName` here must match the exact Kotlin property name.
 * 2. **Annotate guarded code** — place `@BehindFlag("flagName")` on every function, class,
 *    or property that must only run when the flag is active.
 * 3. **Guard call sites** — wrap every call site in an `if`/`when` that checks the flag,
 *    or annotate the containing function/class with `@AssumesFlag("flagName")`.
 * 4. **Let Detekt enforce it** — the `UncheckedFlagAccess` rule (requires
 *    `detektWithTypeResolution`) reports any call site that lacks a valid guard.
 *
 * ## Usage
 *
 * ```kotlin
 * @LocalFlag
 * val newCheckout = ConfigParam<Boolean>("new_checkout", defaultValue = false)
 *
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() { ... }
 *
 * // Call site must be guarded:
 * if (configValues[newCheckout]) {
 *     NewCheckoutScreen()
 * }
 * ```
 *
 * This annotation has [AnnotationRetention.SOURCE] retention — zero runtime overhead.
 *
 * @see AssumesFlag
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class BehindFlag(
    /**
     * The name of the Kotlin property (declared with `@LocalFlag` or `@RemoteFlag`)
     * that guards this declaration. Must match the exact property name, e.g. `"newCheckout"`.
     *
     * Validated by the `InvalidFlagReference` Detekt rule within the same file.
     */
    val flagName: String,
)
