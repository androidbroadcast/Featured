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
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Warns when `@BehindFlag` or `@AssumesFlag` references a flag name that has no matching
 * `@LocalFlag` or `@RemoteFlag` property in the same file.
 *
 * This catches typos in `flagName` at lint time. If the flag registry lives in a different
 * file, the rule produces no warning (no false positives).
 *
 * **Non-compliant:**
 * ```kotlin
 * @BehindFlag("newChekout")  // typo
 * fun NewCheckoutScreen() {}
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * @LocalFlag
 * val newCheckout = ConfigParam("new_checkout", false)
 *
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() {}
 * ```
 */
public class InvalidFlagReference(
    config: Config = Config.empty,
) : Rule(config) {

    override val issue: Issue = Issue(
        id = "InvalidFlagReference",
        severity = Severity.Warning,
        description = "@BehindFlag or @AssumesFlag references an unknown flag name.",
        debt = Debt.FIVE_MINS,
    )

    override fun visit(root: KtFile) {
        // Pass 1: collect @LocalFlag / @RemoteFlag property names in this file
        val knownFlags = root.collectDescendantsOfType<KtProperty>()
            .filter { property ->
                property.annotationEntries.any {
                    it.shortName?.asString() in setOf("LocalFlag", "RemoteFlag")
                }
            }
            .mapNotNull { it.name }
            .toSet()

        // No local flag declarations — nothing to validate against, skip to avoid false positives
        if (knownFlags.isEmpty()) return

        // Pass 2: validate @BehindFlag / @AssumesFlag annotation arguments
        root.collectDescendantsOfType<KtAnnotationEntry>()
            .filter { it.shortName?.asString() in setOf("BehindFlag", "AssumesFlag") }
            .forEach { annotation ->
                val flagName = annotation.valueArguments
                    .firstOrNull()
                    ?.getArgumentExpression()
                    ?.text
                    ?.trim('"')
                    ?: return@forEach

                if (flagName !in knownFlags) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(annotation),
                            message = "Flag name '$flagName' does not match any @LocalFlag or " +
                                "@RemoteFlag property in this file.",
                        )
                    )
                }
            }
    }
}
