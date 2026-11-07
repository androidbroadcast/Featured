package dev.androidbroadcast.featured.sample

import android.content.Context
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AppMetricaSampleStartupTest {
    private val context = mockk<Context>(relaxed = true)
    private val starter = mockk<AppMetricaStarter>(relaxed = true)

    @Test
    fun `blank or missing api key does not call the starter`() {
        AppMetricaSampleStartup.startIfConfigured(context, "", "appmetrica.1", starter)
        AppMetricaSampleStartup.startIfConfigured(context, "   ", "appmetrica.1", starter)
        verify(exactly = 0) { starter.start(any(), any(), any()) }
    }

    @Test
    fun `blank or missing client id does not call the starter`() {
        AppMetricaSampleStartup.startIfConfigured(context, "key", "", starter)
        AppMetricaSampleStartup.startIfConfigured(context, "key", "   ", starter)
        verify(exactly = 0) { starter.start(any(), any(), any()) }
    }

    @Test
    fun `both blank does not call the starter`() {
        AppMetricaSampleStartup.startIfConfigured(context, "", "", starter)
        AppMetricaSampleStartup.startIfConfigured(context, "  ", "  ", starter)
        verify(exactly = 0) { starter.start(any(), any(), any()) }
    }

    @Test
    fun `both non-blank calls the starter with those strings`() {
        AppMetricaSampleStartup.startIfConfigured(context, "key", "appmetrica.9", starter)
        verify(exactly = 1) { starter.start(context, "key", "appmetrica.9") }
    }
}
