@file:Suppress("ktlint:standard:function-naming")

package dev.androidbroadcast.featured

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.androidbroadcast.featured.registry.FlagRegistry

/**
 * Registers all [SampleFeatureFlags] with [FlagRegistry] so that [FeatureFlagsDebugScreen]
 * can discover them via [FlagRegistry.all]. Call once on application start before opening
 * the debug UI. Duplicate calls are safe — the registry ignores already-registered params.
 */
public fun registerSampleFlags() {
    listOf(
        SampleFeatureFlags.mainButtonRed,
        SampleFeatureFlags.newFeatureSectionEnabled,
        SampleFeatureFlags.newCheckout,
        SampleFeatureFlags.promoBannerEnabled,
        SampleFeatureFlags.checkoutVariant,
    ).forEach(FlagRegistry::register)
}

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
