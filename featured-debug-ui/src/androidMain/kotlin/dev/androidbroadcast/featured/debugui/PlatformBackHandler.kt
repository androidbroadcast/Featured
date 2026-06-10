package dev.androidbroadcast.featured.debugui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
