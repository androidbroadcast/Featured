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
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Warns when `@BehindFlag` or `@AssumesFlag` references a flag name that has no matching
 * `ConfigParam` property in the same file.
 *
 * The rule collects all variables/fields whose type or initializer resolves to [ConfigParam],
 * then verifies every `@BehindFlag`/`@AssumesFlag` `flagName` argument matches one of those
 * property names. If the file contains no `ConfigParam` declarations at all, the rule is
 * skipped entirely to avoid false positives from generated code.
 */
public class InvalidFlagReferenceDetector :
    Detector(),
    Detector.UastScanner {
    public companion object {
        public val ISSUE: Issue =
            Issue.create(
                id = "InvalidFlagReference",
                briefDescription = "`@BehindFlag` or `@AssumesFlag` references an unknown flag name",
                explanation = """
                    The `flagName` argument does not match any `ConfigParam` property declared \
                    in the same file. This is likely a typo. \
                    Ensure the value exactly matches the property name of the corresponding \
                    `ConfigParam`.
                """,
                category = Category.CORRECTNESS,
                priority = 7,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        InvalidFlagReferenceDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        private const val CONFIG_PARAM_FQN = "dev.androidbroadcast.featured.ConfigParam"
        private const val BEHIND_FLAG_FQN = "dev.androidbroadcast.featured.BehindFlag"
        private const val ASSUMES_FLAG_FQN = "dev.androidbroadcast.featured.AssumesFlag"
        private const val FLAG_NAME_ATTR = "flagName"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitFile(node: UFile) {
                // Pass 1: collect all property names whose type resolves to ConfigParam.
                val knownFlags = mutableSetOf<String>()
                node.accept(
                    object : AbstractUastVisitor() {
                        override fun visitVariable(node: UVariable): Boolean {
                            val name = node.name ?: return false
                            if (isConfigParam(context, node)) {
                                knownFlags += name
                            }
                            return false
                        }
                    },
                )

                // If the file declares no ConfigParam properties, skip validation entirely.
                // This avoids false positives when flags are imported from generated code.
                if (knownFlags.isEmpty()) return

                // Pass 2: validate @BehindFlag / @AssumesFlag annotations.
                node.accept(
                    object : AbstractUastVisitor() {
                        override fun visitAnnotation(node: UAnnotation): Boolean {
                            val fqn = node.qualifiedName ?: return false
                            if (fqn != BEHIND_FLAG_FQN && fqn != ASSUMES_FLAG_FQN) return false

                            val flagName =
                                node.findAttributeValue(FLAG_NAME_ATTR)?.evaluate() as? String
                                    ?: return false

                            if (flagName !in knownFlags) {
                                context.report(
                                    issue = ISSUE,
                                    scope = node,
                                    location = context.getLocation(node),
                                    message =
                                        "Flag name '$flagName' does not match any `ConfigParam` " +
                                            "property in this file. Known flags: ${knownFlags.sorted().joinToString()}.",
                                )
                            }
                            return false
                        }
                    },
                )
            }
        }

    /**
     * Returns `true` if the variable's resolved type or initializer call refers to [ConfigParam].
     *
     * Two strategies are tried in order:
     * 1. Explicit type annotation resolved via PsiClassType (most reliable).
     * 2. Initializer call text heuristic — fallback for cases where type inference
     *    is not fully resolved in the lint sandbox (same approach as the Detekt rule,
     *    but only as a secondary check).
     */
    private fun isConfigParam(
        context: JavaContext,
        variable: UVariable,
    ): Boolean {
        // Strategy 1: check the declared/inferred type via PSI type resolution.
        val psiType = variable.type as? PsiClassType
        val resolvedClass = psiType?.resolve()
        if (resolvedClass != null &&
            context.evaluator.extendsClass(resolvedClass, CONFIG_PARAM_FQN, false)
        ) {
            return true
        }

        // Strategy 2: heuristic on initializer text for cases where PSI resolution is absent
        // (e.g. inferred type in generated stubs without full classpath). The Detekt rule uses
        // the same fallback since it also lacks full type resolution.
        val initText = variable.uastInitializer?.sourcePsi?.text ?: return false
        return initText.trimStart().startsWith("ConfigParam")
    }
}
