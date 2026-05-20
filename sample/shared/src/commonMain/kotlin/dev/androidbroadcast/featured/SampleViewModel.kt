package dev.androidbroadcast.featured

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidbroadcast.featured.sample.checkout.CheckoutVariant
import dev.androidbroadcast.featured.sample.checkout.checkoutVariantFlow
import dev.androidbroadcast.featured.sample.checkout.newCheckoutFlow
import dev.androidbroadcast.featured.sample.checkout.setNewCheckout
import dev.androidbroadcast.featured.sample.promotions.promoBannerEnabledFlow
import dev.androidbroadcast.featured.sample.promotions.setPromoBannerEnabled
import dev.androidbroadcast.featured.sample.ui.mainButtonRedFlow
import dev.androidbroadcast.featured.sample.ui.newFeatureSectionEnabledFlow
import dev.androidbroadcast.featured.sample.ui.setMainButtonRed
import dev.androidbroadcast.featured.sample.ui.setNewFeatureSectionEnabled
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public class SampleViewModel(
    private val configValues: ConfigValues,
) : ViewModel() {
    public val flagActive: StateFlow<Boolean> =
        configValues
            .mainButtonRedFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)
    // matches default declared in :sample:feature-ui build.gradle.kts

    public val mainButtonColor: StateFlow<MainButtonColor> =
        flagActive
            .map { isRed ->
                if (isRed) MainButtonColor.Red else MainButtonColor.Blue
            }.stateIn(
                initialValue = MainButtonColor.Default,
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            )

    public fun setMainButtonColorFlag(value: Boolean) {
        viewModelScope.launch { configValues.setMainButtonRed(value) }
    }

    public val newFeatureSectionEnabled: StateFlow<Boolean> =
        configValues
            .newFeatureSectionEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)
    // matches default declared in :sample:feature-ui build.gradle.kts

    public val promoBannerEnabled: StateFlow<Boolean> =
        configValues
            .promoBannerEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)
    // matches default declared in :sample:feature-promotions build.gradle.kts

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

    public fun setNewFeatureSectionEnabled(value: Boolean) {
        viewModelScope.launch { configValues.setNewFeatureSectionEnabled(value) }
    }

    public fun setPromoBannerEnabled(value: Boolean) {
        viewModelScope.launch { configValues.setPromoBannerEnabled(value) }
    }

    public fun setNewCheckout(value: Boolean) {
        viewModelScope.launch { configValues.setNewCheckout(value) }
    }

    public sealed interface MainButtonColor {
        public data object Red : MainButtonColor

        public data object Blue : MainButtonColor

        public companion object Companion {
            public val Default: MainButtonColor = Blue
        }
    }
}
