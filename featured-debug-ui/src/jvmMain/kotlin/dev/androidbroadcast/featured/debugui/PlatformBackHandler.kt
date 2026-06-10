package dev.androidbroadcast.featured.debugui

import androidx.compose.runtime.Composable

// No-op: JVM desktop does not have an Android-style system back gesture.
@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
