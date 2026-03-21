package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues

/**
 * Builds a [DebugFlagItem] snapshot for each given [ConfigParam] by reading current values
 * from [configValues].
 */
internal suspend fun buildDebugItems(
    configValues: ConfigValues,
    params: List<ConfigParam<*>>,
): List<DebugFlagItem<*>> = params.map { param -> buildDebugItemForParam(configValues, param) }

internal suspend fun <T : Any> buildDebugItemForParam(
    configValues: ConfigValues,
    param: ConfigParam<T>,
): DebugFlagItem<T> {
    val configValue = configValues.getValue(param)
    val isLocal = configValue.source == ConfigValue.Source.LOCAL
    return DebugFlagItem(
        param = param,
        currentValue = configValue.value,
        overrideValue = if (isLocal) configValue.value else null,
        source = configValue.source,
    )
}
