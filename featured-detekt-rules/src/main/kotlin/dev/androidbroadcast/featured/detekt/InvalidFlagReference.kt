package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Warns when `@BehindFlag` or `@AssumesFlag` references a flag name that has no matching
 * `ConfigParam` property in the same file.
 *
 * This catches typos in `flagName` at lint time. If the flag declarations live in a
 * different file (e.g. in generated code), the rule produces no warning to avoid false
 * positives — it only validates when ConfigParam properties are present in the same file.
 *
 * **Non-compliant:**
 * ```kotlin
 * val newCheckout = ConfigParam("new_checkout", false)
 *
 * @BehindFlag("newChekout")  // typo
 * fun NewCheckoutScreen() {}
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * val newCheckout = ConfigParam("new_checkout", false)
 *
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() {}
 * ```
 */
public class InvalidFlagReference(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "InvalidFlagReference",
            severity = Severity.Warning,
            description = "@BehindFlag or @AssumesFlag references an unknown flag name.",
            debt = Debt.FIVE_MINS,
        )

    override fun visit(root: KtFile) {
        super.visit(root)

        // Collect ConfigParam property names declared in this file.
        // With the Gradle DSL approach, ConfigParams live in generated objects —
        // if none are found here, skip validation to avoid false positives.
        val knownFlags =
            root
                .collectDescendantsOfType<KtProperty>()
                .filter { property -> property.isConfigParam() }
                .mapNotNull { it.name }
                .toSet()

        if (knownFlags.isEmpty()) return

        // Validate @BehindFlag / @AssumesFlag annotation arguments against known names.
        root
            .collectDescendantsOfType<KtAnnotationEntry>()
            .filter { it.shortName?.asString() in setOf("BehindFlag", "AssumesFlag") }
            .forEach { annotation ->
                val flagName =
                    annotation.valueArguments
                        .firstOrNull()
                        ?.getArgumentExpression()
                        ?.let { expr ->
                            val template = expr as? KtStringTemplateExpression ?: return@forEach
                            val entries = template.entries
                            if (entries.size != 1) return@forEach
                            (entries[0] as? KtLiteralStringTemplateEntry)?.text
                        }
                        ?: return@forEach

                if (flagName !in knownFlags) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(annotation),
                            message =
                                "Flag name '$flagName' does not match any ConfigParam property in this file.",
                        ),
                    )
                }
            }
    }
}
