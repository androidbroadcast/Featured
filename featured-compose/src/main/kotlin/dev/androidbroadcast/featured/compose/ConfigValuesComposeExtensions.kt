package dev.androidbroadcast.featured.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.observeValue

@Composable
public fun <T : Any> ConfigValues.collectAsState(param: ConfigParam<T>): State<T> =
    observeValue(param).collectAsState(initial = param.defaultValue)
