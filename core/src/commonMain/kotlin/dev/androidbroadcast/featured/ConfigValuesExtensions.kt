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
/**
 * Returns `true` if the Boolean configuration parameter [param] is currently enabled.
 *
 * This is a convenience wrapper around [ConfigValues.getValue] for [Boolean] parameters,
 * eliminating the need to unwrap the [ConfigValue] envelope at every call site.
 *
 * ```kotlin
 * if (configValues.isEnabled(MyFeatureParam)) {
 *     enableMyFeature()
 * }
 * ```
 *
 * @param param The Boolean configuration parameter to read.
 * @return The current value of [param], or [ConfigParam.defaultValue] when no provider returns one.
 */
public suspend fun ConfigValues.isEnabled(param: ConfigParam<Boolean>): Boolean =
    getValue(param).value

/**
 * Returns a [Flow] that emits the current enabled-state for [param] and updates on every change.
 *
 * This is a convenience wrapper around [observeValue] for [Boolean] parameters.
 *
 * ```kotlin
 * configValues.observeEnabled(MyFeatureParam).collect { enabled ->
 *     if (enabled) showFeature() else hideFeature()
 * }
 * ```
 *
 * @param param The Boolean configuration parameter to observe.
 * @return A [Flow] of [Boolean] values; emits immediately and on every subsequent change.
 */
public fun ConfigValues.observeEnabled(param: ConfigParam<Boolean>): Flow<Boolean> =
    observeValue(param)

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
