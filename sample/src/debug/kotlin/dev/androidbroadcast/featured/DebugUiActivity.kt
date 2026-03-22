package dev.androidbroadcast.featured

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.androidbroadcast.featured.debugui.FeatureFlagsDebugScreen

/**
 * Debug-only activity that hosts the [FeatureFlagsDebugScreen].
 *
 * Launched from [MainActivity] when the user taps "Debug flags".
 * This activity and its dependency on `:featured-debug-ui` are excluded from
 * release builds because `featured-debug-ui` is a `debugImplementation` dependency.
 */
internal class DebugUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Retrieve the shared ConfigValues instance from the application.
        // Cast is safe: SampleApplication is always the application class in this sample.
        val configValues = (application as SampleApplication).configValues

        setContent {
            FeatureFlagsDebugScreen(configValues = configValues)
        }
    }

    internal companion object {
        fun createIntent(context: Context): Intent = Intent(context, DebugUiActivity::class.java)
    }
}
