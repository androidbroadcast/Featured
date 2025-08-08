package dev.androidbroadcast.featured

object SampleFeatureFlags {

    val mainButtonRed = ConfigParam<Boolean>(
        key = "main_button_red",
        defaultValue = true,
        description = "Enable red color for the main button"
    )

    // Add more feature flags as needed
}