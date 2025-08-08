package dev.androidbroadcast.featured

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SampleViewModel(
    private val configValues: ConfigValues
) : ViewModel() {

    val flagActive: StateFlow<Boolean> = configValues.observe(SampleFeatureFlags.mainButtonRed)
        .map { it.value }
        .stateIn(
            initialValue = SampleFeatureFlags.mainButtonRed.defaultValue,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)
        )

    val mainButtonColor = flagActive.map { flagActive ->
        if (flagActive) MainButtonColor.Red else MainButtonColor.Blue
    }.stateIn(
        initialValue = MainButtonColor.Default,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)
    )

    fun setMainButtonColorFlag(value: Boolean) {
        viewModelScope.launch {
            configValues.override(SampleFeatureFlags.mainButtonRed, value)
        }
    }

    sealed interface MainButtonColor {
        data object Red : MainButtonColor
        data object Blue : MainButtonColor

        companion object Companion {

            val Default = Blue
        }
    }
}
