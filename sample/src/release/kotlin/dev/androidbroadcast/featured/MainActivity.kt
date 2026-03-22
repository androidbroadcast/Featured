package dev.androidbroadcast.featured

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Release variant of MainActivity.
 *
 * [onOpenDebugUi] is intentionally null — the debug UI entry point is excluded from
 * release builds. The `:featured-debug-ui` module is a `debugImplementation` dependency
 * and is not present in the release APK/AAB.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val configValues = (application as SampleApplication).configValues

        setContent {
            SampleApp(
                configValues = configValues,
                onOpenDebugUi = null,
            )
        }
    }
}
