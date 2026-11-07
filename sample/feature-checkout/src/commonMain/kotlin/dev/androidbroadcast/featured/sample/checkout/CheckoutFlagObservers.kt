package dev.androidbroadcast.featured.sample.checkout

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsSampleFeatureCheckout
import dev.androidbroadcast.featured.generated.isNewCheckoutEnabled
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun ConfigValues.newCheckoutFlow(): Flow<Boolean> = observe(GeneratedLocalFlagsSampleFeatureCheckout.newCheckout).map { it.value }

public fun ConfigValues.checkoutVariantFlow(): Flow<CheckoutVariant> =
    observe(GeneratedLocalFlagsSampleFeatureCheckout.checkoutVariant).map {
        it.value
    }

public suspend fun ConfigValues.setNewCheckout(value: Boolean) {
    override(GeneratedLocalFlagsSampleFeatureCheckout.newCheckout, value)
}

/**
 * Calls the generated `new_checkout` accessor so release shrinking can delete
 * [NewCheckoutDisabledBranch]. The returned value is never negative; callers use it
 * so the call stays reachable until that accessor is folded.
 */
public fun ConfigValues.reachNewCheckoutDisabledBranch(): Int =
    if (isNewCheckoutEnabled()) {
        NewCheckoutDisabledBranch().marker()
    } else {
        0
    }
