package dev.androidbroadcast.featured.sample.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.androidbroadcast.featured.ConfigValues
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public class UiFlagsViewModel(
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

    public val newFeatureSectionEnabled: StateFlow<Boolean> =
        configValues
            .newFeatureSectionEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)
    // matches default declared in :sample:feature-ui build.gradle.kts

    public fun setMainButtonColorFlag(value: Boolean) {
        viewModelScope.launch { configValues.setMainButtonRed(value) }
    }

    public fun setNewFeatureSectionEnabled(value: Boolean) {
        viewModelScope.launch { configValues.setNewFeatureSectionEnabled(value) }
    }
}
