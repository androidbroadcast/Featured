package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Warns when a [dev.androidbroadcast.featured.ConfigParam] property has neither
 * `@LocalFlag` nor `@RemoteFlag` annotation.
 *
 * Every `ConfigParam` should be explicitly annotated to declare its source,
 * enabling tooling like the Featured Gradle plugin and Detekt rules to reason
 * about the flag correctly.
 *
 * **Non-compliant:**
 * ```kotlin
 * val darkMode = ConfigParam("dark_mode", false)
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * @LocalFlag
 * val darkMode = ConfigParam("dark_mode", false)
 * ```
 */
public class MissingFlagAnnotationRule(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "MissingFlagAnnotation",
            severity = Severity.Warning,
            description = "ConfigParam property is missing a @LocalFlag or @RemoteFlag annotation.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        // Only check top-level or member properties, not local variables inside functions
        if (property.isLocal) return

        if (!property.isConfigParam()) return

        val hasLocalFlag = property.annotationEntries.any { it.shortName?.asString() == "LocalFlag" }
        val hasRemoteFlag = property.annotationEntries.any { it.shortName?.asString() == "RemoteFlag" }

        if (hasLocalFlag || hasRemoteFlag) return

        val propertyName = property.nameIdentifier?.text ?: property.name ?: "unknown"
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(property),
                message = "ConfigParam property '$propertyName' is missing a @LocalFlag or @RemoteFlag annotation.",
            ),
        )
    }
}
