package dev.androidbroadcast.featured.sample.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidbroadcast.featured.ConfigValues
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public class CheckoutFlagsViewModel(
    private val configValues: ConfigValues,
) : ViewModel() {
    public val newCheckout: StateFlow<Boolean> =
        configValues
            .newCheckoutFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)
    // matches default declared in :sample:feature-checkout build.gradle.kts

    public val checkoutVariant: StateFlow<CheckoutVariant> =
        configValues
            .checkoutVariantFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), CheckoutVariant.LEGACY)
    // matches default declared in :sample:feature-checkout build.gradle.kts

    public fun setNewCheckout(value: Boolean) {
        viewModelScope.launch { configValues.setNewCheckout(value) }
    }
}
