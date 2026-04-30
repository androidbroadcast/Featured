package dev.androidbroadcast.featured

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public class SampleViewModel(
    private val configValues: ConfigValues,
) : ViewModel() {
    // --- @LocalFlag: main_button_red ---

    public val flagActive: StateFlow<Boolean> =
        configValues.observeValue(SampleFeatureFlags.mainButtonRed)
            .stateIn(
                initialValue = SampleFeatureFlags.mainButtonRed.defaultValue,
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            )

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

    // --- @LocalFlag: new_feature_section_enabled (isEnabled guard pattern) ---

    /**
     * Controls visibility of the "New Feature" section.
     * Demonstrates the [ConfigParam.isEnabled] guard pattern for entry-point gating.
     */
    public val newFeatureSectionEnabled: StateFlow<Boolean> =
        configValues.observeValue(SampleFeatureFlags.newFeatureSectionEnabled)
            .stateIn(
                initialValue = SampleFeatureFlags.newFeatureSectionEnabled.defaultValue,
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            )

    // --- @RemoteFlag: promo_banner_enabled ---

    /**
     * Whether the promotional banner should be shown.
     * In production this would be driven by Firebase Remote Config.
     */
    public val promoBannerEnabled: StateFlow<Boolean> =
        configValues.observeValue(SampleFeatureFlags.promoBannerEnabled)
            .stateIn(
                initialValue = SampleFeatureFlags.promoBannerEnabled.defaultValue,
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            )

    // --- @RemoteFlag: checkout_variant ---

    /**
     * The active checkout variant, driven remotely.
     * Demonstrates multivariate enum flags resolved from a remote provider.
     */
    public val checkoutVariant: StateFlow<CheckoutVariant> =
        configValues.observeValue(SampleFeatureFlags.checkoutVariant)
            .stateIn(
                initialValue = SampleFeatureFlags.checkoutVariant.defaultValue,
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            )

    public sealed interface MainButtonColor {
        public data object Red : MainButtonColor

        public data object Blue : MainButtonColor

        public companion object Companion {
            public val Default: MainButtonColor = Blue
        }
    }
}

/**
 * Extracts the raw [T] value from a [ConfigValue] stream.
 * Shorthand for `.observe(param).map { it.value }`, used when [stateIn] is chained
 * directly without additional transformation.
 */
private fun <T> ConfigValues.observeValue(param: ConfigParam<T>): Flow<T> =
    observe(param).map { it.value }
