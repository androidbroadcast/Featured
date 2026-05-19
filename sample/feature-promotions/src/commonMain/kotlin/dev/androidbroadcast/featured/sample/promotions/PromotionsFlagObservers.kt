package dev.androidbroadcast.featured.sample.promotions

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedRemoteFlagsSampleFeaturePromotions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun ConfigValues.promoBannerEnabledFlow(): Flow<Boolean> =
    observe(GeneratedRemoteFlagsSampleFeaturePromotions.promoBannerEnabled).map {
        it.value
    }

public suspend fun ConfigValues.setPromoBannerEnabled(value: Boolean) {
    override(GeneratedRemoteFlagsSampleFeaturePromotions.promoBannerEnabled, value)
}
