package dev.androidbroadcast.featured.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

public class HardcodedFlagValueDetector : Detector(), Detector.UastScanner {

    public companion object {
        public val ISSUE: Issue = Issue.create(
            id = "HardcodedFlagValue",
            briefDescription = "Accessing `ConfigParam.defaultValue` directly bypasses providers",
            explanation = """
                Accessing `defaultValue` directly bypasses any local or remote provider \
                overrides, making the flag effectively hardcoded. \
                Use `ConfigValues` to read the live value instead.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                HardcodedFlagValueDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        private const val CONFIG_PARAM_FQN = "dev.androidbroadcast.featured.ConfigParam"
        private const val DEFAULT_VALUE_PROPERTY = "defaultValue"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                // Only care about references named "defaultValue"
                if (node.identifier != DEFAULT_VALUE_PROPERTY) return

                // Must be the selector of a qualified expression: receiver.defaultValue
                val parent = node.uastParent as? UQualifiedReferenceExpression ?: return

                // Resolve the receiver's type
                val receiverType = parent.receiver.getExpressionType() as? PsiClassType ?: return
                val receiverClass = receiverType.resolve() ?: return

                // Fire only when the receiver is ConfigParam or a subclass
                if (!context.evaluator.extendsClass(receiverClass, CONFIG_PARAM_FQN, false)) return

                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "Accessing `defaultValue` directly on a `ConfigParam` bypasses " +
                        "provider overrides. Use `ConfigValues` to read the live value instead.",
                )
            }
        }
}
