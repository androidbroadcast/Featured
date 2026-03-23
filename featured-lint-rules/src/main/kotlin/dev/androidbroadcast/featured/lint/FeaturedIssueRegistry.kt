package dev.androidbroadcast.featured.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registers all Featured Android Lint rules with the Lint infrastructure.
 *
 * Discovered via the `Lint-Registry-v2` JAR manifest attribute.
 * [minApi] is set to 10 (not [CURRENT_API]) so the registry loads on older AGP/Lint
 * hosts without silently dropping all rules.
 */
public class FeaturedIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(HardcodedFlagValueDetector.ISSUE)

    override val api: Int = CURRENT_API

    // minApi = 10 allows AGP consumers on older lint hosts to load the registry.
    // Setting it to CURRENT_API would silently drop all rules for older hosts.
    override val minApi: Int = 10

    override val vendor: Vendor = Vendor(
        vendorName = "Featured",
        feedbackUrl = "https://github.com/AndroidBroadcast/Featured/issues",
    )
}
