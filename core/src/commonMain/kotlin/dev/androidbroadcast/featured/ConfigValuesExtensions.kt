package dev.androidbroadcast.featured

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

public fun <T : Any> ConfigValues.observeValue(param: ConfigParam<T>): Flow<T> =
    observe(param).map { it.value }

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
