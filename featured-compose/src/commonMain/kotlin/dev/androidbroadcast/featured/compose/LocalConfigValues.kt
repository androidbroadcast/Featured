package dev.androidbroadcast.featured.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.androidbroadcast.featured.ConfigValues

/**
 * A [ProvidableCompositionLocal] that holds the current [ConfigValues] instance.
 *
 * Provide a real [ConfigValues] at your app root:
 * ```kotlin
 * CompositionLocalProvider(LocalConfigValues provides configValues) {
 *     App()
 * }
 * ```
 *
 * In any composable, access the current instance:
 * ```kotlin
 * val configValues = LocalConfigValues.current
 * val enabled by configValues.collectAsState(MyFlag)
 * ```
 *
 * In `@Preview` functions, inject a fake without coroutines setup:
 * ```kotlin
 * @Preview
 * @Composable
 * fun MyPreview() {
 *     CompositionLocalProvider(
 *         LocalConfigValues provides fakeConfigValues { set(MyFlag, true) }
 *     ) {
 *         MyScreen()
 *     }
 * }
 * ```
 *
 * The default value is a no-op [ConfigValues] that returns [dev.androidbroadcast.featured.ConfigParam.defaultValue]
 * for all params, so composables that read from [LocalConfigValues.current] work in previews without
 * any explicit provider setup.
 */
public val LocalConfigValues: ProvidableCompositionLocal<ConfigValues> =
    staticCompositionLocalOf { fakeConfigValues() }
