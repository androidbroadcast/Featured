package dev.androidbroadcast.featured

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Debug variant of MainActivity.
 *
 * Wires [onOpenDebugUi] so the "Debug flags" button in [FeaturedSample] launches
 * [DebugUiActivity]. This button is absent in release builds because [onOpenDebugUi]
 * is null there (see release/MainActivity.kt).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val configValues = (application as SampleApplication).configValues

        setContent {
            SampleApp(
                configValues = configValues,
                onOpenDebugUi = {
                    startActivity(DebugUiActivity.createIntent(this))
                },
            )
        }
    }
}
