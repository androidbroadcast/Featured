package dev.androidbroadcast.featured

/**
 * Marks a [ConfigParam] property with the date after which the flag is considered stale
 * and should be cleaned up.
 *
 * ## Intended workflow
 *
 * 1. **Create the flag** — introduce the [ConfigParam] and annotate it with `@ExpiresAt`,
 *    choosing a date far enough in the future to give the team time to roll out and validate
 *    the change.
 * 2. **Merge the feature branch into `main`** — once validated, merge the annotated flag into
 *    the main branch and ship from there.
 * 3. **Expiry date arrives** — static analysis tooling (for example, a Detekt rule such as
 *    `ExpiresAtRule`, if integrated) can warn at build time for any flag whose `@ExpiresAt`
 *    date is in the past, surfacing stale flags automatically in CI.
 * 4. **Clean up** — remove the flag declaration, its usages, and any associated remote
 *    configuration entries to complete the lifecycle.
 *
 * ## Usage
 *
 * ```kotlin
 * @LocalFlag
 * @ExpiresAt("2026-06-01")
 * val newCheckout = ConfigParam<Boolean>("new_checkout", defaultValue = false)
 * ```
 *
 * The [date] must be an ISO-8601 calendar date (`YYYY-MM-DD`). No runtime parsing is
 * performed — validation is delegated entirely to external tooling.
 *
 * This annotation has [AnnotationRetention.SOURCE] retention, so it incurs zero runtime
 * overhead and is not available at runtime or retained in compiled artifacts.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
public annotation class ExpiresAt(
    /**
     * The expiry date in ISO-8601 format (`YYYY-MM-DD`), e.g. `"2026-06-01"`.
     *
     * When this date is in the past, static analysis tooling can raise a warning
     * indicating the flag should be removed.
     */
    val date: String,
)
