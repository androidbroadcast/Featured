package dev.androidbroadcast.featured.sample.promotions

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedRemoteFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun ConfigValues.promoBannerEnabledFlow(): Flow<Boolean> =
    observe(GeneratedRemoteFlags.promoBannerEnabled).map { it.value }

public suspend fun ConfigValues.setPromoBannerEnabled(value: Boolean) {
    override(GeneratedRemoteFlags.promoBannerEnabled, value)
}
