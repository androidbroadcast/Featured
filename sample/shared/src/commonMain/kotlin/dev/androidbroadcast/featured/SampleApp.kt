@file:Suppress("ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Root composable for the sample application.
 *
 * [onOpenDebugUi] is non-null in debug builds (wired by the debug source set)
 * and null in release builds, so no debug UI entry point is compiled into release.
 *
 * @param configValues The shared [ConfigValues] instance.
 * @param onOpenDebugUi Callback to navigate to the debug UI screen. Null in release builds.
 * @param modifier Optional [Modifier] for the root composable.
 */
@Composable
public fun SampleApp(
    configValues: ConfigValues,
    onOpenDebugUi: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FeaturedSample(
        configValues = configValues,
        onOpenDebugUi = onOpenDebugUi,
        modifier = modifier,
    )
}
