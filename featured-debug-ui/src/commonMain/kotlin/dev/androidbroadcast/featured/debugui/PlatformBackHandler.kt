package dev.androidbroadcast.featured.debugui

import androidx.compose.runtime.Composable

/**
 * Platform-specific back-press handler.
 *
 * On Android, intercepts the system back gesture when [enabled] is true and invokes [onBack].
 * On iOS and JVM desktop, this is a no-op (those platforms handle back differently or lack it).
 */
@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
