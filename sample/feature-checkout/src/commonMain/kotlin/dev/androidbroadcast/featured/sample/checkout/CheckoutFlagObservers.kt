package dev.androidbroadcast.featured.sample.checkout

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsSampleFeatureCheckout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun ConfigValues.newCheckoutFlow(): Flow<Boolean> = observe(GeneratedLocalFlagsSampleFeatureCheckout.newCheckout).map { it.value }

public fun ConfigValues.checkoutVariantFlow(): Flow<CheckoutVariant> = observe(GeneratedLocalFlagsSampleFeatureCheckout.checkoutVariant).map { it.value }

public suspend fun ConfigValues.setNewCheckout(value: Boolean) {
    override(GeneratedLocalFlagsSampleFeatureCheckout.newCheckout, value)
}
