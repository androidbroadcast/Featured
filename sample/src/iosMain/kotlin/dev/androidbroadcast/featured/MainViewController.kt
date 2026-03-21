@file:Suppress("RedundantVisibilityModifier", "ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

public fun MainViewController(): UIViewController =
    ComposeUIViewController {
        FeaturedSample()
    }
