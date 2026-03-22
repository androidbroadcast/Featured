@file:JvmName("MainDesktop")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// ConfigValues is constructed once at the application entry point and passed
// explicitly — the recommended pattern for multi-module apps using DI.
fun main() {
    val configValues = ConfigValues(localProvider = InMemoryConfigValueProvider())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Featured",
        ) {
            FeaturedSample(configValues = configValues)
        }
    }
}
