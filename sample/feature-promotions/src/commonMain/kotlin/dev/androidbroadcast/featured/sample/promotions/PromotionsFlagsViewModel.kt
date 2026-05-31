package dev.androidbroadcast.featured.sample.promotions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidbroadcast.featured.ConfigValues
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public class PromotionsFlagsViewModel(
    private val configValues: ConfigValues,
) : ViewModel() {
    public val promoBannerEnabled: StateFlow<Boolean> =
        configValues
            .promoBannerEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)
    // matches default declared in :sample:feature-promotions build.gradle.kts

    public fun setPromoBannerEnabled(value: Boolean) {
        viewModelScope.launch { configValues.setPromoBannerEnabled(value) }
    }
}
