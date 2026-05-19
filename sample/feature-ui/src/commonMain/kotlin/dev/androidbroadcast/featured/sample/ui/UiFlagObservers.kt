package dev.androidbroadcast.featured.sample.ui

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedLocalFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun ConfigValues.mainButtonRedFlow(): Flow<Boolean> =
    observe(GeneratedLocalFlags.mainButtonRed).map { it.value }

public suspend fun ConfigValues.setMainButtonRed(value: Boolean) {
    override(GeneratedLocalFlags.mainButtonRed, value)
}

public fun ConfigValues.newFeatureSectionEnabledFlow(): Flow<Boolean> =
    observe(GeneratedLocalFlags.newFeatureSectionEnabled).map { it.value }

public suspend fun ConfigValues.setNewFeatureSectionEnabled(value: Boolean) {
    override(GeneratedLocalFlags.newFeatureSectionEnabled, value)
}
