package dev.androidbroadcast.featured

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen
import dev.androidbroadcast.featured.platform.defaultLocalProvider

class MainActivity : ComponentActivity() {
    // Singleton ConfigValues for this process — DataStore must not be opened more than once
    // per file per process; lazy + applicationContext satisfies that contract.
    private val configValues by lazy {
        ConfigValues(
            localProvider = defaultLocalProvider(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
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
}
