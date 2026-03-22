package dev.androidbroadcast.featured.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.observeValue

/**
 * Collects the current value for [param] from this [ConfigValues] as Compose [State].
 *
 * The returned [State] is initialised with [ConfigParam.defaultValue] and updates
 * automatically whenever the underlying [ConfigValues.observe] flow emits a new value.
 *
 * ```kotlin
 * @Composable
 * fun MyScreen(configValues: ConfigValues) {
 *     val isDarkMode by configValues.collectAsState(DarkModeParam)
 *     // recomposes whenever the flag changes
 * }
 * ```
 *
 * @param param The configuration parameter to observe.
 * @return A [State] whose value is always the latest resolved configuration value.
 */
@Composable
public fun <T : Any> ConfigValues.collectAsState(param: ConfigParam<T>): State<T> =
    observeValue(param).collectAsState(initial = param.defaultValue)
