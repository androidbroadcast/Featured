package dev.androidbroadcast.featured.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers the Featured custom Detekt rules under the `featured` rule set id.
 *
 * To enable in your project, add the artifact to Detekt's classpath and include
 * the rule set in your `detekt.yml`:
 *
 * ```yaml
 * featured:
 *   ExpiredFeatureFlag:
 *     active: true
 *   HardcodedFlagValue:
 *     active: true
 *   MissingFlagAnnotation:
 *     active: true
 * ```
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
                ),
        )
}
