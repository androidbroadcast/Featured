@file:Suppress("RedundantVisibilityModifier")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

public fun MainViewController(): UIViewController = ComposeUIViewController {
    FeaturedSample()
}