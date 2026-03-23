package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement

private val BEHIND_FLAG_FQN = FqName("dev.androidbroadcast.featured.BehindFlag")
private val FLAG_NAME_PARAM = Name.identifier("flagName")
private const val BEHIND_FLAG_SHORT = "BehindFlag"
private const val ASSUMES_FLAG_SHORT = "AssumesFlag"

/**
 * Warns when a `@BehindFlag("X")`-annotated function or constructor is called outside
 * a valid feature-flag context.
 *
 * **Requires type resolution.** Run via `./gradlew detektWithTypeResolution` (or the
 * target-specific variant for KMP: `detektWithTypeResolutionCommonMain`, etc.).
 * When run without type resolution (`BindingContext.EMPTY`), the rule silently skips all
 * checks to avoid false positives.
 *
 * **Valid contexts** (checked by walking up the PSI tree from the call site):
 * - Enclosing `if`/`when` whose condition references the flag by name.
 * - Enclosing function or class annotated `@BehindFlag("X")` for the same flag.
 * - Enclosing function or class annotated `@AssumesFlag("X")` for the same flag.
 *
 * **Non-compliant:**
 * ```kotlin
 * @BehindFlag("newCheckout")
 * fun NewCheckoutScreen() { ... }
 *
 * fun host() { NewCheckoutScreen() }  // missing flag guard
 * ```
 *
 * **Compliant:**
 * ```kotlin
 * if (configValues[newCheckout]) { NewCheckoutScreen() }
 * ```
 */
public class UncheckedFlagAccess(
    config: Config = Config.empty,
) : Rule(config) {
    override val issue: Issue =
        Issue(
            id = "UncheckedFlagAccess",
            severity = Severity.Warning,
            description = "@BehindFlag-annotated code used outside a feature-flag guard.",
            debt = Debt.TWENTY_MINS,
        )

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        super.visitSimpleNameExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return
        // Skip callee expressions inside direct calls — handled by visitCallExpression.
        // Callable references (this::fn) are intentionally NOT excluded here; accessing
        // a @BehindFlag declaration via reference outside a guard is itself a violation.
        if (expression.parent is KtCallExpression) return

        val descriptor =
            bindingContext[BindingContext.REFERENCE_TARGET, expression]
                ?: return

        val flagName =
            descriptor.behindFlagNameViaDescriptor()
                ?: descriptor.behindFlagNameViaPsi()
                ?: return

        if (!expression.isInValidFlagContext(flagName)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message =
                        "Access to '${descriptor.name}' is not guarded by flag '$flagName'. " +
                            "Wrap in if/when checking '$flagName', or annotate the containing scope " +
                            "with @BehindFlag(\"$flagName\") or @AssumesFlag(\"$flagName\").",
                ),
            )
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (bindingContext == BindingContext.EMPTY) return

        val resolvedCall = expression.getResolvedCall(bindingContext) ?: return
        val descriptor = resolvedCall.resultingDescriptor

        // Primary: resolve flag name via BindingContext (works cross-module in production).
        // Fallback: read PSI annotation by short name (works in unit tests where the
        // annotation class itself is not on the test classpath).
        val flagName =
            descriptor.behindFlagNameViaDescriptor()
                ?: descriptor.behindFlagNameViaPsi()
                ?: return

        if (!expression.isInValidFlagContext(flagName)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message =
                        "Call to '${descriptor.name}' is not guarded by flag '$flagName'. " +
                            "Wrap in if/when checking '$flagName', or annotate the containing scope " +
                            "with @BehindFlag(\"$flagName\") or @AssumesFlag(\"$flagName\").",
                ),
            )
        }
    }

    // ── Annotation resolution ─────────────────────────────────────────────────

    /** Reads `@BehindFlag` from the descriptor's annotation list (requires annotation on classpath). */
    private fun DeclarationDescriptor.behindFlagNameViaDescriptor(): String? =
        annotations
            .findAnnotation(BEHIND_FLAG_FQN)
            ?.allValueArguments
            ?.get(FLAG_NAME_PARAM)
            ?.value as? String

    /**
     * Fallback: resolves the declaration's PSI source and reads `@BehindFlag` by short name.
     * Used when the annotation class is not on the classpath (e.g. in unit tests).
     */
    private fun DeclarationDescriptor.behindFlagNameViaPsi(): String? {
        val psi =
            ((this as? DeclarationDescriptorWithSource)?.source as? KotlinSourceElement)?.psi
                ?: return null
        return when (psi) {
            is KtNamedFunction -> psi.annotationEntries.behindFlagName()
            is KtClassOrObject -> psi.annotationEntries.behindFlagName()
            is KtProperty -> psi.annotationEntries.behindFlagName()
            else -> null
        }
    }

    private fun List<KtAnnotationEntry>.behindFlagName(): String? =
        firstOrNull { it.shortName?.asString() == BEHIND_FLAG_SHORT }
            ?.flagNameArgument()

    // ── PSI context validation ────────────────────────────────────────────────

    private fun PsiElement.isInValidFlagContext(flagName: String): Boolean {
        var node: PsiElement? = parent
        while (node != null) {
            when {
                // if (...flagName...) { call() }
                node is KtIfExpression && node.condition.containsFlagReference(flagName) -> return true

                // when { flagName -> { call() } }
                node is KtWhenEntry &&
                    node.conditions.any { cond ->
                        cond.containsFlagReference(flagName)
                    }
                -> return true

                // Enclosing function with @BehindFlag("X") or @AssumesFlag("X")
                node is KtNamedFunction && node.hasGuardAnnotation(flagName) -> return true

                // Enclosing class/object with @BehindFlag("X") or @AssumesFlag("X")
                // KtClassOrObject covers both `class` and `object`; companion objects are
                // already short-circuited by the branch below, so exclude them here.
                node is KtClassOrObject && !(node is KtObjectDeclaration && node.isCompanion()) &&
                    node.hasGuardAnnotation(flagName) -> return true

                // Crossed into a companion object — class annotation does not cover this scope
                node is KtObjectDeclaration && node.isCompanion() -> return false
            }
            node = node.parent
        }
        return false
    }

    private fun PsiElement?.containsFlagReference(flagName: String): Boolean {
        if (this == null) return false
        // Check the element itself (e.g. bare `if (newCheckout)` where condition IS the reference)
        if (this is KtNameReferenceExpression && this.getReferencedName() == flagName) return true
        // Check all descendants
        return PsiTreeUtil
            .findChildrenOfType(this, KtNameReferenceExpression::class.java)
            .any { it.getReferencedName() == flagName }
    }

    private fun KtNamedFunction.hasGuardAnnotation(flagName: String): Boolean = annotationEntries.any { it.matchesGuard(flagName) }

    private fun KtClassOrObject.hasGuardAnnotation(flagName: String): Boolean = annotationEntries.any { it.matchesGuard(flagName) }

    private fun KtAnnotationEntry.matchesGuard(flagName: String): Boolean {
        val name = shortName?.asString() ?: return false
        if (name !in setOf(BEHIND_FLAG_SHORT, ASSUMES_FLAG_SHORT)) return false
        return flagNameArgument() == flagName
    }

    private fun KtAnnotationEntry.flagNameArgument(): String? {
        val entries =
            valueArguments
                .firstOrNull()
                ?.getArgumentExpression()
                ?.let { it as? KtStringTemplateExpression }
                ?.entries ?: return null
        if (entries.size != 1) return null
        return (entries[0] as? KtLiteralStringTemplateEntry)?.text
    }
}
