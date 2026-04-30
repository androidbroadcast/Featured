package dev.androidbroadcast.featured

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public class SampleViewModel(
    private val configValues: ConfigValues,
) : ViewModel() {
    public val flagActive: StateFlow<Boolean> =
        configValues.asStateFlow(SampleFeatureFlags.mainButtonRed, viewModelScope)

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
        viewModelScope.launch {
            configValues.override(SampleFeatureFlags.mainButtonRed, value)
        }
    }

    /**
     * Controls visibility of the "New Feature" section.
     * Demonstrates the [ConfigParam.isEnabled] guard pattern for entry-point gating.
     */
    public val newFeatureSectionEnabled: StateFlow<Boolean> =
        configValues.asStateFlow(SampleFeatureFlags.newFeatureSectionEnabled, viewModelScope)

    /**
     * Whether the promotional banner should be shown.
     * In production this would be driven by Firebase Remote Config.
     */
    public val promoBannerEnabled: StateFlow<Boolean> =
        configValues.asStateFlow(SampleFeatureFlags.promoBannerEnabled, viewModelScope)

    /**
     * The active checkout variant, driven remotely.
     * Demonstrates multivariate enum flags resolved from a remote provider.
     */
    public val checkoutVariant: StateFlow<CheckoutVariant> =
        configValues.asStateFlow(SampleFeatureFlags.checkoutVariant, viewModelScope)

    public sealed interface MainButtonColor {
        public data object Red : MainButtonColor

        public data object Blue : MainButtonColor

        public companion object Companion {
            public val Default: MainButtonColor = Blue
        }
    }
}
