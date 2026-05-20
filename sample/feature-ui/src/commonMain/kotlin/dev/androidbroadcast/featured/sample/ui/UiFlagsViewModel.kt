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
    public val mainButtonColor: StateFlow<MainButtonColor> =
        configValues
            .mainButtonRedFlow()
            .map { isRed -> if (isRed) MainButtonColor.Red else MainButtonColor.Blue }
            .stateIn(
                // matches the default declared in :sample:feature-ui build.gradle.kts
                // (main_button_red = true → Red)
                initialValue = MainButtonColor.Red,
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            )

    public val newFeatureSectionEnabled: StateFlow<Boolean> =
        configValues
            .newFeatureSectionEnabledFlow()
            // matches default declared in :sample:feature-ui build.gradle.kts
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), true)

    public fun setMainButtonColor(color: MainButtonColor) {
        viewModelScope.launch { configValues.setMainButtonRed(color == MainButtonColor.Red) }
    }

    public fun setNewFeatureSectionEnabled(value: Boolean) {
        viewModelScope.launch { configValues.setNewFeatureSectionEnabled(value) }
    }
}
