package dev.androidbroadcast.featured.lint

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClassType
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
        private const val EXPIRES_AT_SIMPLE = "ExpiresAt"
        private const val CONFIG_PARAM_FQN = "dev.androidbroadcast.featured.ConfigParam"
    }

    override fun applicableAnnotations(): List<String> = listOf(EXPIRES_AT_SIMPLE, EXPIRES_AT_FQN)

    // Only fire for DEFINITION events (annotation declared on the element itself).
    // Without this override visitAnnotationUsage fires for call-site usages too, but
    // @ExpiresAt is @Retention(SOURCE) so we only care about the declaration.
    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = type == AnnotationUsageType.DEFINITION

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo,
    ) {
        // For DEFINITION events, `element` is the UAnnotation node itself.
        // Navigate up to the annotated UVariable (property or field).
        // See: KotlinNullnessAnnotationDetector in lint-checks for this pattern.
        val variable = element.uastParent as? UVariable ?: return

        // Restrict to ConfigParam-typed properties, mirroring the Detekt counterpart.
        val variableType = variable.type as? PsiClassType ?: return
        val variableClass = variableType.resolve() ?: return
        if (!context.evaluator.extendsClass(variableClass, CONFIG_PARAM_FQN, false)) return

        // Extract the date string from the annotation's first argument.
        val annotation = annotationInfo.annotation
        val dateArg = annotation.findAttributeValue("date")?.evaluate() as? String ?: return

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
            scope = element,
            location = context.getLocation(element),
            message = "Feature flag '$flagName' expired on $dateArg. Remove the flag and its guarded code.",
        )
    }
}
