package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Warns when [dev.androidbroadcast.featured.ConfigParam.defaultValue] is accessed directly
 * instead of reading the value through `ConfigValues`.
 *
 * Accessing `defaultValue` directly bypasses any local or remote provider overrides,
 * making the flag effectively hardcoded.
 *
 * **Non-compliant:**
 * ```kotlin
 * if (newCheckout.defaultValue) { ... }
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * val enabled: Boolean by configValues[newCheckout]
 * ```
 */
public class HardcodedFlagValueRule(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "HardcodedFlagValue",
            severity = Severity.Warning,
            description = "Accessing ConfigParam.defaultValue directly bypasses providers. Use ConfigValues instead.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return
        if (selector.getReferencedName() != "defaultValue") return

        // Check if receiver looks like a ConfigParam reference
        val receiver = expression.receiverExpression
        if (!receiver.isLikelyConfigParam()) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message =
                    "Accessing 'defaultValue' directly on a ConfigParam bypasses provider overrides. " +
                        "Use ConfigValues to read the live value instead.",
            ),
        )
    }
}
