package dev.androidbroadcast.featured

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Returns a [Flow] that emits the unwrapped value for [param] on every change.
 *
 * This is a convenience wrapper around [ConfigValues.observe] that strips the
 * [ConfigValue] envelope so callers only see the raw [T] values.
 *
 * ```kotlin
 * configValues.observeValue(DarkModeParam).collect { isDark ->
 *     applyTheme(isDark)
 * }
 * ```
 *
 * @param param The configuration parameter to observe.
 * @return A [Flow] of unwrapped values; emits immediately and on every subsequent change.
 */
public fun <T : Any> ConfigValues.observeValue(param: ConfigParam<T>): Flow<T> = observe(param).map { it.value }

/**
 * Converts the [ConfigValues.observe] flow for [param] into a [StateFlow].
 *
 * The [StateFlow] is shared within [scope] according to the [started] policy. The initial
 * value is [ConfigParam.defaultValue], ensuring the [StateFlow] is never empty.
 *
 * ```kotlin
 * val isDark: StateFlow<Boolean> = configValues.asStateFlow(
 *     param   = DarkModeParam,
 *     scope   = viewModelScope,
 *     started = SharingStarted.WhileSubscribed(5_000),
 * )
 * ```
 *
 * @param param The configuration parameter to observe.
 * @param scope The [CoroutineScope] that governs the sharing coroutine's lifetime.
 * @param started Controls when sharing starts and stops; defaults to
 *   [SharingStarted.WhileSubscribed] with a 5-second replay timeout.
 * @return A [StateFlow] whose value is always the latest unwrapped configuration value.
 */
public fun <T : Any> ConfigValues.asStateFlow(
    param: ConfigParam<T>,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
): StateFlow<T> =
    observeValue(param).stateIn(
        scope = scope,
        started = started,
        initialValue = param.defaultValue,
    )
