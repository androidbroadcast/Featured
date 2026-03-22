package dev.androidbroadcast.featured

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// ConfigValues is constructed once here and passed explicitly — the recommended
// pattern for multi-module apps using any DI framework (Hilt, Koin, manual).
// In a real app this instance would come from your DI graph rather than being
// created directly in onCreate.
class MainActivity : ComponentActivity() {
    private val configValues: ConfigValues =
        ConfigValues(
            localProvider = InMemoryConfigValueProvider(),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            FeaturedSample(configValues = configValues)
        }
    }
}
