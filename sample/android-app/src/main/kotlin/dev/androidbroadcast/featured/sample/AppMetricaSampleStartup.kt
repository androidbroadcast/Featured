package dev.androidbroadcast.featured.sample

import android.content.Context
import com.yandex.varioqub.appmetricaadapter.AppMetricaAdapter
import com.yandex.varioqub.config.Varioqub
import com.yandex.varioqub.config.VarioqubSettings
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

internal fun interface AppMetricaStarter {
    fun start(
        context: Context,
        apiKey: String,
        clientId: String,
    )
}

internal object OfficialAppMetricaStarter : AppMetricaStarter {
    override fun start(
        context: Context,
        apiKey: String,
        clientId: String,
    ) {
        AppMetrica.activate(context, AppMetricaConfig.newConfigBuilder(apiKey).build())
        Varioqub.init(
            VarioqubSettings.Builder(clientId).build(),
            AppMetricaAdapter(context),
            context,
        )
    }
}

internal object AppMetricaSampleStartup {
    fun startIfConfigured(
        context: Context,
        apiKey: String,
        clientId: String,
        starter: AppMetricaStarter = OfficialAppMetricaStarter,
    ) {
        if (apiKey.isBlank() || clientId.isBlank()) return
        starter.start(context, apiKey, clientId)
    }
}
