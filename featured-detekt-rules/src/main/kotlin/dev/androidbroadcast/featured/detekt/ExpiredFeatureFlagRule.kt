package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtProperty
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Warns when a `@ExpiresAt`-annotated [dev.androidbroadcast.featured.ConfigParam] property
 * has passed its expiry date.
 *
 * Feature flags with an expiry date should be removed once that date has passed to
 * avoid accumulating stale flags in the codebase.
 *
 * **Non-compliant:**
 * ```kotlin
 * @ExpiresAt("2020-01-01")
 * val oldFlag = ConfigParam("old_flag", false)
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * @ExpiresAt("2099-12-31")
 * val futureFlag = ConfigParam("future_flag", false)
 * ```
 */
public class ExpiredFeatureFlagRule(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "ExpiredFeatureFlag",
            severity = Severity.Warning,
            description = "Feature flag has passed its expiry date and should be removed.",
            debt = Debt.TWENTY_MINS,
        )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        val expiresAtAnnotation =
            property.annotationEntries.firstOrNull { annotation ->
                annotation.shortName?.asString() == "ExpiresAt"
            } ?: return

        // Only check properties that are ConfigParam
        if (!property.isConfigParam()) return

        val dateArg =
            expiresAtAnnotation.valueArguments
                .firstOrNull()
                ?.getArgumentExpression()
                ?.text
                ?.trim('"')
                ?: return

        val expiryDate =
            try {
                LocalDate.parse(dateArg)
            } catch (_: DateTimeParseException) {
                return
            }

        val today = LocalDate.now()
        if (!today.isAfter(expiryDate)) return

        val flagName = property.nameIdentifier?.text ?: property.name ?: "unknown"
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(property),
                message = "Feature flag '$flagName' expired on $dateArg. Remove the flag and its guarded code.",
            ),
        )
    }
}
