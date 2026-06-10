package dev.androidbroadcast.featured.debugui

import androidx.compose.runtime.Composable

/**
 * Platform-specific back-press handler.
 *
 * On Android, intercepts the system back gesture when [enabled] is true and invokes [onBack].
 * On iOS and JVM desktop, this is a no-op (those platforms handle back differently or lack it).
 * As a result, the search-collapse-on-back affordance is absent on those platforms; additionally,
 * if [enabled] was true when the screen left composition, [rememberSaveable] may restore
 * [isSearchActive] to true on re-entry, re-opening search without a back gesture to dismiss it.
 */
@Composable
@Suppress("ktlint:standard:function-naming")
internal expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
