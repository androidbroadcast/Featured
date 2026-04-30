@file:Suppress("RedundantVisibilityModifier", "ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

// ConfigValues is constructed once per UIViewController and passed explicitly.
// In a real app this instance would come from a shared DI container.
public fun MainViewController(): UIViewController {
    val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())
    return ComposeUIViewController {
        FeaturedSample(configValues = configValues)
    }
}
