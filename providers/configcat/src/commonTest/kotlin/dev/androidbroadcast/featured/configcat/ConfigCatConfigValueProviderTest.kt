package dev.androidbroadcast.featured.configcat

import com.configcat.ClientCacheState
import com.configcat.ConfigCatClient
import com.configcat.ConfigCatClientSnapshot
import com.configcat.ConfigCatUser
import com.configcat.EvaluationDetails
import com.configcat.Hooks
import com.configcat.fetch.RefreshErrorCode
import com.configcat.fetch.RefreshResult
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConfigCatConfigValueProviderTest {
    // ---------------------------------------------------------------------------
    // Fake ConfigCatClient — returns values from a simple map
    // ---------------------------------------------------------------------------

    private class FakeConfigCatClient(
        private val values: Map<String, Any?> = emptyMap(),
    ) : ConfigCatClient {
        var refreshCalled = false

        override suspend fun getAnyValue(
            key: String,
            defaultValue: Any?,
            user: ConfigCatUser?,
        ): Any? = if (values.containsKey(key)) values[key] else defaultValue

        override suspend fun getAnyValueDetails(
            key: String,
            defaultValue: Any?,
            user: ConfigCatUser?,
        ): EvaluationDetails = throw UnsupportedOperationException()

        override suspend fun getAllValueDetails(user: ConfigCatUser?): Collection<EvaluationDetails> = throw UnsupportedOperationException()

        override suspend fun getKeyAndValue(variationId: String): Pair<String, Any>? = null

        override suspend fun getAllKeys(): Collection<String> = values.keys

        override suspend fun getAllValues(user: ConfigCatUser?): Map<String, Any?> = values

        override suspend fun forceRefresh(): RefreshResult {
            refreshCalled = true
            return RefreshResult(true, null, RefreshErrorCode.NONE, null)
        }

        override fun setOnline(): Unit = Unit

        override fun setOffline(): Unit = Unit

        override val isOffline: Boolean get() = false
        override val hooks: Hooks get() = Hooks()

        override fun setDefaultUser(user: ConfigCatUser): Unit = Unit

        override fun clearDefaultUser(): Unit = Unit

        override fun close(): Unit = Unit

        override fun isClosed(): Boolean = false

        override suspend fun waitForReady(): ClientCacheState = ClientCacheState.NO_FLAG_DATA

        override fun snapshot(): ConfigCatClientSnapshot = throw UnsupportedOperationException()
    }

    // ---------------------------------------------------------------------------
    // Tests — get()
    // ---------------------------------------------------------------------------

    @Test
    fun `get returns REMOTE ConfigValue for Boolean flag`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("dark_mode" to true))
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "dark_mode", defaultValue = false)

            val result = provider.get(param)

            assertEquals(ConfigValue(value = true, source = ConfigValue.Source.REMOTE), result)
        }

    @Test
    fun `get returns REMOTE ConfigValue for String flag`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("theme" to "dark"))
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "theme", defaultValue = "light")

            val result = provider.get(param)

            assertEquals(ConfigValue(value = "dark", source = ConfigValue.Source.REMOTE), result)
        }

    @Test
    fun `get returns REMOTE ConfigValue for Int flag`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("max_retries" to 5))
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "max_retries", defaultValue = 3)

            val result = provider.get(param)

            assertEquals(ConfigValue(value = 5, source = ConfigValue.Source.REMOTE), result)
        }

    @Test
    fun `get returns REMOTE ConfigValue for Long flag converted from Double`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("timeout_ms" to 5000.0))
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "timeout_ms", defaultValue = 3000L)

            val result = provider.get(param)

            assertEquals(ConfigValue(value = 5000L, source = ConfigValue.Source.REMOTE), result)
        }

    @Test
    fun `get returns REMOTE ConfigValue for Double flag`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("threshold" to 0.85))
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "threshold", defaultValue = 0.5)

            val result = provider.get(param)

            assertEquals(ConfigValue(value = 0.85, source = ConfigValue.Source.REMOTE), result)
        }

    @Test
    fun `get returns REMOTE ConfigValue for Float flag converted from Double`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("ratio" to 0.75))
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "ratio", defaultValue = 0.5f)

            val result = provider.get(param)

            assertEquals(ConfigValue(value = 0.75f, source = ConfigValue.Source.REMOTE), result)
        }

    @Test
    fun `get returns null when key is absent in ConfigCat`() =
        runTest {
            val client = FakeConfigCatClient(values = emptyMap())
            val provider = ConfigCatConfigValueProvider(client)
            val param = ConfigParam(key = "missing_flag", defaultValue = false)

            val result = provider.get(param)

            assertNull(result)
        }

    @Test
    fun `get throws IllegalArgumentException for unsupported type`() =
        runTest {
            val client = FakeConfigCatClient(values = mapOf("custom" to "value"))
            val provider = ConfigCatConfigValueProvider(client)

            data class CustomType(
                val x: Int,
            )

            val param = ConfigParam(key = "custom", defaultValue = CustomType(0))

            assertFailsWith<IllegalArgumentException> {
                provider.get(param)
            }
        }

    // ---------------------------------------------------------------------------
    // Tests — fetch()
    // ---------------------------------------------------------------------------

    @Test
    fun `fetch calls forceRefresh on ConfigCatClient`() =
        runTest {
            val client = FakeConfigCatClient()
            val provider = ConfigCatConfigValueProvider(client)

            provider.fetch(activate = true)

            assertEquals(true, client.refreshCalled)
        }

    @Test
    fun `fetch with activate=false still calls forceRefresh`() =
        runTest {
            val client = FakeConfigCatClient()
            val provider = ConfigCatConfigValueProvider(client)

            provider.fetch(activate = false)

            assertEquals(true, client.refreshCalled)
        }
}
