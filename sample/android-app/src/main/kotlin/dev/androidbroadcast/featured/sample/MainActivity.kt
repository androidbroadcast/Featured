package dev.androidbroadcast.featured.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.SampleApp
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen
import dev.androidbroadcast.featured.platform.defaultLocalProvider

class MainActivity : ComponentActivity() {
    // ConfigValues is held at Activity scope for this sample.
    // In production, move to Application or a DI singleton to avoid
    // recreating (and re-opening) the DataStore file on every rotation.
    private val configValues by lazy {
        ConfigValues(localProvider = defaultLocalProvider(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showDebug by rememberSaveable { mutableStateOf(false) }

            if (showDebug) {
                BackHandler { showDebug = false }
                FeatureFlagsDebugScreen(configValues = configValues)
            } else {
                SampleApp(
                    configValues = configValues,
                    onOpenDebugUi = { showDebug = true },
                )
            }
        }
    }
}
