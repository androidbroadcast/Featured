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
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable
import java.time.LocalDate
import java.time.format.DateTimeParseException

public class ExpiredFeatureFlagDetector :
    Detector(),
    Detector.UastScanner {
    public companion object {
        public val ISSUE: Issue =
            Issue.create(
                id = "ExpiredFeatureFlag",
                briefDescription = "Feature flag has passed its expiry date and should be removed",
                explanation = """
                    A `@ExpiresAt`-annotated `ConfigParam` property has passed its expiry date. \
                    Remove the flag and all code guarded by it to avoid accumulating stale flags.
                """,
                category = Category.CORRECTNESS,
                priority = 5,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        ExpiredFeatureFlagDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        private const val EXPIRES_AT_FQN = "dev.androidbroadcast.featured.ExpiresAt"
        private const val CONFIG_PARAM_FQN = "dev.androidbroadcast.featured.ConfigParam"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UAnnotation::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            // Kotlin @Target(PROPERTY, FIELD) causes UAST to visit the same KtAnnotationEntry
            // twice: once as the Kotlin property annotation, once as the backing-field annotation.
            // Both have non-null sourcePsi pointing to the same KtAnnotationEntry object.
            // We deduplicate by tracking visited sourcePsi instances within this file handler.
            private val visitedSourceElements = mutableSetOf<Any>()

            override fun visitAnnotation(node: UAnnotation) {
                // Only care about @ExpiresAt annotations.
                val qualifiedName = node.qualifiedName ?: return
                if (qualifiedName != EXPIRES_AT_FQN && !qualifiedName.endsWith(".ExpiresAt")) return

                // Navigate up to the annotated UVariable (property or field).
                val variable = node.uastParent as? UVariable ?: return

                // Deduplicate: both Kotlin-property and backing-field visits share the same
                // KtAnnotationEntry as sourcePsi. Track each sourcePsi and skip repeats.
                val sourcePsi = node.sourcePsi ?: node.javaPsi ?: return
                if (!visitedSourceElements.add(sourcePsi)) return

                // Restrict to ConfigParam-typed properties, mirroring the Detekt counterpart.
                val variableType = variable.type as? PsiClassType ?: return
                val variableClass = variableType.resolve() ?: return
                if (!context.evaluator.extendsClass(variableClass, CONFIG_PARAM_FQN, false)) return

                // Extract the date string from the annotation's first positional argument.
                val dateArg = node.findAttributeValue("date")?.evaluate() as? String ?: return

                val expiryDate =
                    try {
                        LocalDate.parse(dateArg)
                    } catch (_: DateTimeParseException) {
                        // Malformed date — skip silently, do not crash.
                        return
                    }

                // today.isAfter(expiryDate) matches Detekt semantics:
                //   past  → today > expiryDate → report
                //   today → today == expiryDate → clean (not after)
                //   future → today < expiryDate → clean
                if (!LocalDate.now().isAfter(expiryDate)) return

                val flagName = variable.name ?: "unknown"
                context.report(
                    issue = ISSUE,
                    scope = node,
                    location = context.getLocation(node),
                    message = "Feature flag '$flagName' expired on $dateArg. Remove the flag and its guarded code.",
                )
            }
        }
}
