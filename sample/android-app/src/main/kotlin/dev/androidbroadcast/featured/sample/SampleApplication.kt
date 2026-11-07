package dev.androidbroadcast.featured.sample

import android.app.Application
import dev.androidbroadcast.featured.sample.app.BuildConfig

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppMetricaSampleStartup.startIfConfigured(
            context = this,
            apiKey = BuildConfig.APPMETRICA_API_KEY,
            clientId = BuildConfig.APPMETRICA_CLIENT_ID,
        )
    }
}
