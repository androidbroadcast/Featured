@file:JvmName("MainDesktop")

package dev.androidbroadcast.featured

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Featured",
    ) {
        FeaturedSample()
    }
}