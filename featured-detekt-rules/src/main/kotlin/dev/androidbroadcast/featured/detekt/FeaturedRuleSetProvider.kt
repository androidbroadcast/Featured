package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Provides the `featured` Detekt rule set.
 *
 * Rules:
 * - [ExpiredFeatureFlagRule] — flags past their expiry date
 * - [HardcodedFlagValueRule] — hardcoded boolean flag values
 * - [MissingFlagAnnotationRule] — missing `@LocalFlag`/`@RemoteFlag` annotations
 * - [InvalidFlagReference] — `@BehindFlag`/`@AssumesFlag` referencing an unknown flag name (PSI-only, runs under plain `detekt` task)
 * - [UncheckedFlagAccess] — `@BehindFlag`-annotated code used outside a guard (requires `detektWithTypeResolution` task)
 *
 * Example `detekt.yml`:
 * ```yaml
 * featured:
 *   ExpiredFeatureFlag:
 *     active: true
 *   HardcodedFlagValue:
 *     active: true
 *   MissingFlagAnnotation:
 *     active: true
 *   InvalidFlagReference:
 *     active: true      # runs under plain detekt task
 *   UncheckedFlagAccess:
 *     active: true      # requires detektWithTypeResolution task
 * ```
 *
 * Note: [UncheckedFlagAccess] requires the `detektWithTypeResolution` Gradle task to resolve
 * cross-file and cross-module references. It silently skips checks when run without type resolution.
 */
public class FeaturedRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "featured"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            id = ruleSetId,
            rules =
                listOf(
                    ExpiredFeatureFlagRule(config),
                    HardcodedFlagValueRule(config),
                    MissingFlagAnnotationRule(config),
                    InvalidFlagReference(config),
                    UncheckedFlagAccess(config),
                ),
        )
}
