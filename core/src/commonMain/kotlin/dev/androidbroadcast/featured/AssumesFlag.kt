package dev.androidbroadcast.featured

/**
 * Marks a function or class that takes explicit responsibility for ensuring the named feature
 * flag is checked before execution reaches this scope.
 *
 * ## Purpose
 *
 * When a function or class always runs within a guarded context but cannot express that guard
 * directly in its own body (e.g., a navigation host that conditionally renders flag-gated
 * screens), annotate it with `@AssumesFlag` to suppress `UncheckedFlagAccess` warnings for
 * call sites of `@BehindFlag`-annotated code inside this scope.
 *
 * ## Scope
 *
 * - On a **function**: suppresses warnings inside the function body.
 * - On a **class**: suppresses warnings inside member functions and `init` blocks.
 *   Companion object members are **not** covered — they are a separate scope.
 *
 * ## ⚠️ Escape hatch
 *
 * This annotation is **not verified**. The Detekt rule trusts the annotation without
 * checking that an actual flag guard exists inside the scope. Misuse silently bypasses
 * `UncheckedFlagAccess`. Use it only when the calling context genuinely guarantees the
 * flag is checked.
 *
 * ## Usage
 *
 * ```kotlin
 * @AssumesFlag("newCheckout")
 * fun CheckoutNavHost(configValues: ConfigValues) {
 *     // This function is only called when newCheckout is enabled upstream.
 *     NewCheckoutScreen()  // no UncheckedFlagAccess warning here
 * }
 * ```
 *
 * This annotation has [AnnotationRetention.SOURCE] retention — zero runtime overhead.
 *
 * @see BehindFlag
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class AssumesFlag(
    /**
     * The name of the feature flag property this scope guarantees is checked before execution.
     * Must match the `flagName` of the corresponding `@BehindFlag` declaration.
     */
    val flagName: String,
)
