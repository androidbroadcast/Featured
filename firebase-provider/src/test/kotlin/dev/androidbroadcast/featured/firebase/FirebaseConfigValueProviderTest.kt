package dev.androidbroadcast.featured.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class FirebaseConfigValueProviderTest {
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private lateinit var provider: FirebaseConfigValueProvider

    @Before
    fun setUp() {
        remoteConfig = mockk(relaxed = true)
        provider = FirebaseConfigValueProvider(remoteConfig)
    }

    // --- get() source mapping ---

    @Test
    fun `get returns ConfigValue with REMOTE source when value is from remote`() =
        runTest {
            val param = ConfigParam("feature_flag", "default")
            val mockValue = mockk<FirebaseRemoteConfigValue>()
            every { mockValue.source } returns FirebaseRemoteConfig.VALUE_SOURCE_REMOTE
            every { mockValue.asString() } returns "remote_value"
            every { remoteConfig.getValue("feature_flag") } returns mockValue

            val result = requireNotNull(provider.get(param))

            assertEquals(ConfigValue.Source.REMOTE, result.source)
            assertEquals("remote_value", result.value)
        }

    @Test
    fun `get returns ConfigValue with REMOTE_DEFAULT source when value is from default`() =
        runTest {
            val param = ConfigParam("feature_flag", "default")
            val mockValue = mockk<FirebaseRemoteConfigValue>()
            every { mockValue.source } returns FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT
            every { mockValue.asString() } returns "default_value"
            every { remoteConfig.getValue("feature_flag") } returns mockValue

            val result = requireNotNull(provider.get(param))

            assertEquals(ConfigValue.Source.REMOTE_DEFAULT, result.source)
        }

    @Test
    fun `get returns ConfigValue with DEFAULT source when value is static`() =
        runTest {
            val param = ConfigParam("feature_flag", "default")
            val mockValue = mockk<FirebaseRemoteConfigValue>()
            every { mockValue.source } returns FirebaseRemoteConfig.VALUE_SOURCE_STATIC
            every { mockValue.asString() } returns ""
            every { remoteConfig.getValue("feature_flag") } returns mockValue

            val result = requireNotNull(provider.get(param))

            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    // --- Type conversions ---

    @Test
    fun `get converts String value correctly`() =
        runTest {
            val param = ConfigParam("string_key", "fallback")
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asString() } returns "hello"
            every { remoteConfig.getValue("string_key") } returns mockValue

            val result = provider.get(param)

            assertEquals("hello", result!!.value)
        }

    @Test
    fun `get converts Boolean value correctly`() =
        runTest {
            val param = ConfigParam("bool_key", false)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asBoolean() } returns true
            every { remoteConfig.getValue("bool_key") } returns mockValue

            val result = provider.get(param)

            assertEquals(true, result!!.value)
        }

    @Test
    fun `get converts Int value correctly`() =
        runTest {
            val param = ConfigParam("int_key", 0)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asLong() } returns 42L
            every { remoteConfig.getValue("int_key") } returns mockValue

            val result = provider.get(param)

            assertEquals(42, result!!.value)
        }

    @Test
    fun `get converts Long value correctly`() =
        runTest {
            val param = ConfigParam("long_key", 0L)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asLong() } returns 9876543210L
            every { remoteConfig.getValue("long_key") } returns mockValue

            val result = provider.get(param)

            assertEquals(9876543210L, result!!.value)
        }

    @Test
    fun `get converts Double value correctly`() =
        runTest {
            val param = ConfigParam("double_key", 0.0)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asDouble() } returns 3.14159
            every { remoteConfig.getValue("double_key") } returns mockValue

            val result = provider.get(param)

            assertEquals(3.14159, result!!.value, 0.000001)
        }

    @Test
    fun `get converts Float value correctly`() =
        runTest {
            val param = ConfigParam("float_key", 0f)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asDouble() } returns 2.718
            every { remoteConfig.getValue("float_key") } returns mockValue

            val result = provider.get(param)

            assertEquals(2.718f, result!!.value, 0.001f)
        }

    // --- Int overflow guard ---

    @Test
    fun `get throws IllegalArgumentException when long value overflows Int range`() =
        runTest {
            val param = ConfigParam("int_key", 0)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asLong() } returns Long.MAX_VALUE
            every { remoteConfig.getValue("int_key") } returns mockValue

            assertFailsWith<IllegalArgumentException> { provider.get(param) }
        }

    // --- Float non-finite guard ---

    @Test
    fun `get throws IllegalArgumentException when double value is NaN for Float`() =
        runTest {
            val param = ConfigParam("float_key", 0f)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asDouble() } returns Double.NaN
            every { remoteConfig.getValue("float_key") } returns mockValue

            assertFailsWith<IllegalArgumentException> { provider.get(param) }
        }

    // --- Custom converter ---

    @Test
    fun `get uses custom converter registered in converters registry`() =
        runTest {
            val param = ConfigParam("theme_key", Theme.LIGHT)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asString() } returns "DARK"
            every { remoteConfig.getValue("theme_key") } returns mockValue

            provider.converters.put<Theme>(Converter { Theme.valueOf(it.asString()) })

            val result = provider.get(param)

            assertEquals(Theme.DARK, result!!.value)
        }

    // --- Enum auto-conversion ---

    @Test
    fun `get auto-converts enum by name when no explicit converter registered`() =
        runTest {
            val param = ConfigParam("theme_key", Theme.LIGHT)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asString() } returns "DARK"
            every { remoteConfig.getValue("theme_key") } returns mockValue

            val result = provider.get(param)

            assertEquals(Theme.DARK, result!!.value)
        }

    @Test
    fun `get throws when enum constant name is unknown`() =
        runTest {
            val param = ConfigParam("theme_key", Theme.LIGHT)
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asString() } returns "INVALID_THEME"
            every { remoteConfig.getValue("theme_key") } returns mockValue

            assertFailsWith<IllegalArgumentException> { provider.get(param) }
        }

    // --- Unregistered type ---

    @Test
    fun `get throws IllegalStateException when no converter registered for type`() =
        runTest {
            data class CustomType(
                val x: Int,
            )
            val param = ConfigParam("custom_key", CustomType(0))
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { remoteConfig.getValue("custom_key") } returns mockValue

            assertFailsWith<IllegalStateException> { provider.get(param) }
        }

    // --- fetch() behaviour ---

    @Test
    fun `fetch with activate=true calls fetchAndActivate`() =
        runTest {
            every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)

            provider.fetch(activate = true)

            verify(exactly = 1) { remoteConfig.fetchAndActivate() }
            verify(exactly = 0) { remoteConfig.fetch() }
        }

    @Test
    fun `fetch with activate=false calls fetch only`() =
        runTest {
            every { remoteConfig.fetch() } returns Tasks.forResult(null)

            provider.fetch(activate = false)

            verify(exactly = 1) { remoteConfig.fetch() }
            verify(exactly = 0) { remoteConfig.fetchAndActivate() }
        }

    @Test
    fun `fetch defaults to activate=true`() =
        runTest {
            every { remoteConfig.fetchAndActivate() } returns Tasks.forResult(true)

            provider.fetch()

            verify(exactly = 1) { remoteConfig.fetchAndActivate() }
        }

    // --- Network error propagation ---

    @Test
    fun `fetch propagates exception when fetchAndActivate task fails`() =
        runTest {
            val networkError = RuntimeException("Network unavailable")
            every { remoteConfig.fetchAndActivate() } returns Tasks.forException(networkError)

            assertFailsWith<FetchException> { provider.fetch(activate = true) }
        }

    @Test
    fun `fetch propagates exception when fetch task fails`() =
        runTest {
            val networkError = RuntimeException("Network unavailable")
            every { remoteConfig.fetch() } returns Tasks.forException(networkError)

            assertFailsWith<FetchException> { provider.fetch(activate = false) }
        }

    // --- Converters registry ---

    @Test
    fun `converters registry contains built-in converters for all primitive types`() {
        assertTrue(String::class in provider.converters)
        assertTrue(Boolean::class in provider.converters)
        assertTrue(Int::class in provider.converters)
        assertTrue(Long::class in provider.converters)
        assertTrue(Double::class in provider.converters)
        assertTrue(Float::class in provider.converters)
    }

    @Test
    fun `custom converter replaces existing converter for same type`() =
        runTest {
            val param = ConfigParam("string_key", "default")
            val mockValue = mockRemoteValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE)
            every { mockValue.asString() } returns "raw"
            every { remoteConfig.getValue("string_key") } returns mockValue

            provider.converters.put<String>(Converter { "overridden_${it.asString()}" })

            val result = provider.get(param)

            assertEquals("overridden_raw", result!!.value)
        }

    // --- Helpers ---

    private fun mockRemoteValue(source: Int): FirebaseRemoteConfigValue {
        val value = mockk<FirebaseRemoteConfigValue>()
        every { value.source } returns source
        return value
    }
}

private enum class Theme { LIGHT, DARK }
