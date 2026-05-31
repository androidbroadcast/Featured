package dev.androidbroadcast.featured.sample.ui

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsSampleFeatureUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun ConfigValues.mainButtonRedFlow(): Flow<Boolean> = observe(GeneratedLocalFlagsSampleFeatureUi.mainButtonRed).map { it.value }

public suspend fun ConfigValues.setMainButtonRed(value: Boolean) {
    override(GeneratedLocalFlagsSampleFeatureUi.mainButtonRed, value)
}

public fun ConfigValues.newFeatureSectionEnabledFlow(): Flow<Boolean> =
    observe(GeneratedLocalFlagsSampleFeatureUi.newFeatureSectionEnabled).map { it.value }

public suspend fun ConfigValues.setNewFeatureSectionEnabled(value: Boolean) {
    override(GeneratedLocalFlagsSampleFeatureUi.newFeatureSectionEnabled, value)
}
