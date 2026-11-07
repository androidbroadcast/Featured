package dev.androidbroadcast.featured.sample

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yandex.varioqub.appmetricaadapter.AppMetricaAdapter
import com.yandex.varioqub.config.Varioqub
import com.yandex.varioqub.config.VarioqubSettings
import io.appmetrica.analytics.AppMetrica
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class OfficialAppMetricaStarterTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `start activates AppMetrica then initializes Varioqub`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // AppMetricaConfig rejects a non-UUID key before activate runs. This is a fixture, not a project key.
        val apiKey = "00000000-0000-0000-0000-000000000001"
        val clientId = "appmetrica.42"
        mockkStatic(AppMetrica::class)
        mockkStatic(Varioqub::class)
        every { AppMetrica.activate(any(), any()) } just runs
        every { Varioqub.init(any(), any(), any()) } just runs

        OfficialAppMetricaStarter.start(context, apiKey, clientId)

        verifyOrder {
            AppMetrica.activate(context, match { it.apiKey == apiKey })
            Varioqub.init(
                match { clientIdOf(it) == clientId },
                any<AppMetricaAdapter>(),
                context,
            )
        }
    }

    private fun clientIdOf(settings: VarioqubSettings): String {
        val method = settings.javaClass.getMethod("getClientId\$config_release")
        val clientId = method.invoke(settings) as String
        assertEquals("appmetrica.42", clientId)
        return clientId
    }
}
