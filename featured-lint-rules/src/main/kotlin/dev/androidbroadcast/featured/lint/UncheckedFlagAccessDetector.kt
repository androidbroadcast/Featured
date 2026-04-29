package dev.androidbroadcast.featured.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects calls to functions annotated `@BehindFlag("flagName")` that are not guarded
 * by a valid feature-flag context.
 *
 * **Valid guard contexts (v1):**
 * 1. Call is inside an `if`/`when` expression whose subject or condition contains a
 *    reference to the flag by name (the string value of `flagName` appears as an
 *    identifier in the condition/subject).
 * 2. Call is inside a function annotated `@BehindFlag("sameFlagName")`.
 *
 * **Excluded from v1:** callable references, companion object scope escape,
 * `@AssumesFlag` as a valid guard context.
 */
public class UncheckedFlagAccessDetector :
    Detector(),
    Detector.UastScanner {
    public companion object {
        public val ISSUE: Issue =
            Issue.create(
                id = "UncheckedFlagAccess",
                briefDescription = "Call to @BehindFlag-annotated code outside a feature-flag guard",
                explanation = """
                    Calling a function or constructor annotated `@BehindFlag("flagName")` outside \
                    a valid guard context means the flag is never checked before execution. \
                    Wrap the call in an `if`/`when` that references `flagName`, or annotate the \
                    containing function with `@BehindFlag("flagName")`.
                """,
                category = Category.CORRECTNESS,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        UncheckedFlagAccessDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        private const val BEHIND_FLAG_FQN = "dev.androidbroadcast.featured.BehindFlag"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val psiMethod: PsiMethod = node.resolve() ?: return

                // @BehindFlag has SOURCE retention — annotations are not available via
                // descriptor/reflection at runtime. Read directly from PSI annotation list.
                val annotation: PsiAnnotation =
                    psiMethod.getAnnotation(BEHIND_FLAG_FQN)
                        ?: psiMethod.annotations.firstOrNull { it.qualifiedName?.endsWith("BehindFlag") == true }
                        ?: return

                val flagNameValue = annotation.findAttributeValue("flagName") ?: return
                // ConstantEvaluator handles KtStringTemplateExpression arguments that
                // PsiLiteralExpression casts would miss in Kotlin sources.
                val flagName: String =
                    ConstantEvaluator.evaluate(context, flagNameValue) as? String ?: return

                if (isInValidFlagContext(node, flagName)) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message =
                        "Call to '${psiMethod.name}' is not guarded by flag '$flagName'. " +
                            "Wrap in if/when checking '$flagName', or annotate the containing " +
                            "function with @BehindFlag(\"$flagName\").",
                )
            }
        }

    private fun isInValidFlagContext(
        node: UCallExpression,
        flagName: String,
    ): Boolean {
        // Guard #2: enclosing function annotated @BehindFlag("sameFlagName").
        val containingMethod: UMethod? = node.getContainingUMethod()
        if (containingMethod != null) {
            val methodAnnotation =
                containingMethod.javaPsi.getAnnotation(BEHIND_FLAG_FQN)
                    ?: containingMethod.javaPsi.annotations
                        .firstOrNull { it.qualifiedName?.endsWith("BehindFlag") == true }
            if (methodAnnotation != null) {
                val value = methodAnnotation.findAttributeValue("flagName")
                // No context available here; evaluate without a context — literal strings
                // evaluate to themselves without needing a JavaContext.
                // PsiAnnotationMemberValue extends PsiElement; ?: return skips null values.
                val psiValue = value as? com.intellij.psi.PsiElement ?: return true
                val enclosingFlagName = ConstantEvaluator.evaluate(null, psiValue) as? String
                if (enclosingFlagName == flagName) return true
            }
        }

        // Guard #1: enclosed in an if/when whose condition/subject references flagName.
        var parent = node.uastParent
        while (parent != null) {
            when (parent) {
                is UIfExpression -> {
                    if (parent.condition.containsFlagReference(flagName)) return true
                }

                is USwitchExpression -> {
                    // Check the switch subject (e.g. `when (newCheckout) { ... }`).
                    if (parent.expression?.containsFlagReference(flagName) == true) return true
                }
            }
            parent = parent.uastParent
        }
        return false
    }

    /**
     * Returns true if this UAST element or any descendant is a simple name reference
     * whose identifier equals [flagName].
     *
     * Uses accept() with an AbstractUastVisitor for recursive tree walk —
     * UElement has no direct uastContents property in this lint API version.
     */
    private fun UElement.containsFlagReference(flagName: String): Boolean {
        var found = false
        accept(
            object : AbstractUastVisitor() {
                override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                    if (node.identifier == flagName) found = true
                    return found // stop early once found
                }
            },
        )
        return found
    }
}
