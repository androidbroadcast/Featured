package dev.androidbroadcast.featured.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

public class FeaturedIssueRegistry : IssueRegistry() {

    override val issues = listOf(HardcodedFlagValueDetector.ISSUE)

    override val api: Int = CURRENT_API

    // minApi = 10 allows AGP consumers on older lint hosts to load the registry.
    // Setting it to CURRENT_API would silently drop all rules for older hosts.
    override val minApi: Int = 10

    override val vendor: Vendor = Vendor(
        vendorName = "Featured",
        feedbackUrl = "https://github.com/AndroidBroadcast/Featured/issues",
    )
}
