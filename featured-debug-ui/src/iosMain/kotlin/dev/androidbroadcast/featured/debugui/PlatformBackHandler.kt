package dev.androidbroadcast.featured.debugui

import androidx.compose.runtime.Composable

// No-op: iOS back navigation is handled by the SwiftUI/UIKit host, not Compose.
@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
