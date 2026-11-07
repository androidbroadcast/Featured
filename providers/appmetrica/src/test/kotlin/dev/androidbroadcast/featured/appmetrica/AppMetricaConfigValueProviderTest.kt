package dev.androidbroadcast.featured.appmetrica

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yandex.varioqub.config.FetchError
import com.yandex.varioqub.config.OnFetchCompleteListener
import com.yandex.varioqub.config.VarioqubApi
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.yandex.varioqub.config.model.ConfigValue as VarioqubConfigValue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class AppMetricaConfigValueProviderTest {
    private lateinit var client: VarioqubApi
    private lateinit var provider: AppMetricaConfigValueProvider

    @Before
    fun setUp() {
        client = mockk(relaxed = true)
        provider = AppMetricaConfigValueProvider(client)
    }

    @Test
    fun `get maps SOURCE_SERVER to REMOTE and returns the SDK string`() =
        runTest {
            assertEquals(2, varioqubSource("SOURCE_SERVER"))
            val param = ConfigParam("feature_flag", "declared-default")
            val sdkValue = stub("feature_flag", "remote_value", varioqubSource("SOURCE_SERVER"))

            val result = provider.get(param)

            assertEquals(ConfigValue.Source.REMOTE, result.source)
            assertEquals(sdkValue.asString(), result.value)
            assertEquals("remote_value", result.value)
            assertNotEquals(param.defaultValue, result.value)
            verify { client.getValue("feature_flag") }
            verify(exactly = 0) { client.init(any(), any(), any()) }
        }

    @Test
    fun `get maps SOURCE_INAPP_DEFAULT to REMOTE_DEFAULT`() =
        runTest {
            assertEquals(1, varioqubSource("SOURCE_INAPP_DEFAULT"))
            val param = ConfigParam("feature_flag", "declared-default")
            stub("feature_flag", "inapp", varioqubSource("SOURCE_INAPP_DEFAULT"))

            val result = provider.get(param)

            assertEquals(ConfigValue.Source.REMOTE_DEFAULT, result.source)
            assertEquals("inapp", result.value)
        }

    @Test
    fun `get maps SOURCE_DEFAULT to DEFAULT and does not substitute the parameter default`() =
        runTest {
            assertEquals(0, varioqubSource("SOURCE_DEFAULT"))
            val param = ConfigParam("feature_flag", "declared-default")
            val sdkValue = stub("feature_flag", "sdk-value", varioqubSource("SOURCE_DEFAULT"))

            val result = provider.get(param)

            assertNotNull(result)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
            assertEquals(sdkValue.asString(), result.value)
            assertNotEquals(param.defaultValue, result.value)
        }

    @Test
    fun `get maps an unknown source int to UNKNOWN`() =
        runTest {
            val param = ConfigParam("feature_flag", "declared-default")
            stub("feature_flag", "mystery", 99)

            val result = provider.get(param)

            assertEquals(ConfigValue.Source.UNKNOWN, result.source)
            assertEquals("mystery", result.value)
        }

    @Test
    fun `get converts built-in types from the SDK value`() =
        runTest {
            assertEquals("hello", read(ConfigParam("string_key", "fallback"), "hello"))
            assertEquals(true, read(ConfigParam("bool_key", false), "true"))
            assertEquals(42, read(ConfigParam("int_key", 0), "42"))
            assertEquals(Int.MIN_VALUE, read(ConfigParam("int_min", 0), Int.MIN_VALUE.toString()))
            assertEquals(Int.MAX_VALUE, read(ConfigParam("int_max", 0), Int.MAX_VALUE.toString()))
            assertEquals(9_876_543_210L, read(ConfigParam("long_key", 0L), "9876543210"))
            assertEquals("3.14159".toDouble(), read(ConfigParam("double_key", 0.0), "3.14159"))
            val finite = read(ConfigParam("float_key", 0f), "2.718")
            assertEquals("2.718".toDouble().toFloat(), finite)
            val overflow = read(ConfigParam("float_overflow", 0f), "1.0e39")
            assertEquals("1.0e39".toDouble().toFloat(), overflow)
        }

    @Test
    fun `get throws IllegalArgumentException when long value overflows Int range`() =
        runTest {
            val param = ConfigParam("int_key", 0)
            stub("int_key", (Int.MAX_VALUE.toLong() + 1).toString(), varioqubSource("SOURCE_SERVER"))

            assertFailsWith<IllegalArgumentException> { provider.get(param) }
        }

    @Test
    fun `get throws IllegalArgumentException when Float source is not finite`() =
        runTest {
            listOf("NaN", "Infinity", "-Infinity").forEach { raw ->
                stub("float_key", raw, varioqubSource("SOURCE_SERVER"))
                assertFailsWith<IllegalArgumentException> { provider.get(ConfigParam("float_key", 0f)) }
            }
        }

    @Test
    fun `get resolves an enum by name and rejects an unknown name`() =
        runTest {
            stub("theme_key", "DARK", varioqubSource("SOURCE_SERVER"))
            assertEquals(Theme.DARK, provider.get(ConfigParam("theme_key", Theme.LIGHT)).value)

            stub("theme_key", "INVALID_THEME", varioqubSource("SOURCE_SERVER"))
            assertFailsWith<IllegalArgumentException> { provider.get(ConfigParam("theme_key", Theme.LIGHT)) }
        }

    @Test
    fun `registered converter replaces the built-in and a later put replaces it`() =
        runTest {
            val param = ConfigParam("string_key", "default")
            stub("string_key", "raw", varioqubSource("SOURCE_SERVER"))
            provider.converters.put<String>(Converter { "first_${it.asString()}" })

            assertEquals("first_raw", provider.get(param).value)

            provider.converters.put<String>(Converter { "second_${it.asString()}" })

            assertEquals("second_raw", provider.get(param).value)
        }

    @Test
    fun `registered enum converter wins over name resolution`() =
        runTest {
            stub("theme_key", "DARK", varioqubSource("SOURCE_SERVER"))
            provider.converters.put<Theme>(Converter { Theme.LIGHT })

            assertEquals(Theme.LIGHT, provider.get(ConfigParam("theme_key", Theme.DARK)).value)
        }

    @Test
    fun `converter IllegalArgumentException propagates unwrapped`() =
        runTest {
            stub("string_key", "raw", varioqubSource("SOURCE_SERVER"))
            val failure = IllegalArgumentException("bad")
            provider.converters.put<String>(Converter { throw failure })

            val thrown = assertFailsWith<IllegalArgumentException> { provider.get(ConfigParam("string_key", "default")) }

            assertSame(failure, thrown)
        }

    @Test
    fun `get throws IllegalStateException when no converter is registered for a non-enum`() =
        runTest {
            stub("custom_key", "raw", varioqubSource("SOURCE_SERVER"))

            assertFailsWith<IllegalStateException> { provider.get(ConfigParam("custom_key", CustomType(0))) }
        }

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
    fun `get initialize and fetch do not call init`() =
        runTest {
            every { client.getValue(any()) } returns server("value")
            every { client.fetchConfig(any()) } answers {
                firstArg<OnFetchCompleteListener>().onSuccess()
            }
            every { client.activateConfig(any()) } answers {
                firstArg<() -> Unit>().invoke()
            }

            provider.get(ConfigParam("k", "d"))
            provider.initialize()
            provider.fetch(activate = true)
            provider.fetch(activate = false)
            provider.fetch()

            verify(exactly = 0) { client.init(any(), any(), any()) }
        }

    @Test
    fun `provider sources and gradle file do not start the vendor or call typed getters`() {
        val root = moduleDir()
        val sources =
            root
                .resolve("src/main")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .joinToString(separator = "\n") { it.readText() }
        listOf(
            "Varioqub.init",
            "AppMetrica.activate",
            "setDefaults",
            "getString",
            "getBoolean",
            "getLong",
            "getDouble",
        ).forEach { token ->
            assertFalse(sources.contains(token), "main sources must not contain $token")
        }
        val gradle = root.resolve("build.gradle.kts").readText()
        assertFalse(gradle.contains("appmetrica-adapter"))
        assertFalse(gradle.contains("io.appmetrica.analytics"))
    }

    @Test
    fun `initialize calls activateConfig once and waits for the callback`() =
        runTest {
            val script = ClientScript(client)
            val job = launch { provider.initialize() }
            runCurrent()

            assertTrue(job.isActive)
            verify(exactly = 1) { client.activateConfig(any()) }
            verify(exactly = 0) { client.fetchConfig(any()) }

            script.activate()
            runCurrent()

            assertNull(job.completionCause())
            assertTrue(job.isCompleted)
        }

    @Test
    fun `initialize wraps a non-cancellation exception from activateConfig`() =
        runTest {
            val cause = IllegalStateException("disk")
            every { client.activateConfig(any()) } throws cause

            val thrown = assertFailsWith<AppMetricaConfigException> { provider.initialize() }

            assertSame(cause, thrown.cause)
        }

    @Test
    fun `initialize rethrows CancellationException from activateConfig`() =
        runTest {
            val cancellation = CancellationException("stop")
            every { client.activateConfig(any()) } throws cancellation

            val thrown = assertFailsWith<CancellationException> { provider.initialize() }

            assertSame(cancellation, thrown)
        }

    @Test
    fun `cancelling initialize then invoking the activate callback stays cancelled`() =
        runTest {
            val script = ClientScript(client)
            val job = launch { provider.initialize() }
            runCurrent()

            job.cancel()
            runCurrent()
            script.activate()
            runCurrent()

            assertCancelledNotWrapped(job.completionCause())
        }

    @Test
    fun `fetch with activate true waits for fetch success and then the activate callback`() =
        runTest {
            val script = ClientScript(client)
            val job = launch { provider.fetch(activate = true) }
            runCurrent()

            assertTrue(job.isActive)
            verify(exactly = 1) { client.fetchConfig(any()) }
            verify(exactly = 0) { client.activateConfig(any()) }

            script.fetchListener.onSuccess()
            runCurrent()

            assertTrue(job.isActive)
            verify(exactly = 1) { client.activateConfig(any()) }

            script.activate()
            runCurrent()

            assertNull(job.completionCause())
        }

    @Test
    fun `fetch with no argument activates after a successful download`() =
        runTest {
            val script = ClientScript(client)
            val job = launch { provider.fetch() }
            runCurrent()
            script.fetchListener.onSuccess()
            runCurrent()

            verify(exactly = 1) { client.fetchConfig(any()) }
            verify(exactly = 1) { client.activateConfig(any()) }
            assertTrue(job.isActive)

            script.activate()
            runCurrent()

            assertNull(job.completionCause())
        }

    @Test
    fun `fetch with activate false does not call activateConfig`() =
        runTest {
            val script = ClientScript(client)
            val job = launch { provider.fetch(activate = false) }
            runCurrent()
            script.fetchListener.onSuccess()
            runCurrent()

            verify(exactly = 1) { client.fetchConfig(any()) }
            verify(exactly = 0) { client.activateConfig(any()) }
            assertNull(job.completionCause())
        }

    @Test
    fun `onError throws AppMetricaConfigException and does not activate`() =
        runTest {
            assertFetchError("throttled", FetchError.REQUEST_THROTTLED)
            assertFetchError("offline", FetchError.NETWORK_ERROR)
        }

    @Test
    fun `CancellationException from fetchConfig is not wrapped`() =
        runTest {
            val cancellation = CancellationException("stop")
            every { client.fetchConfig(any()) } throws cancellation

            assertSame(cancellation, assertFailsWith<CancellationException> { provider.fetch(activate = true) })
            assertSame(cancellation, assertFailsWith<CancellationException> { provider.fetch(activate = false) })
        }

    @Test
    fun `fetch wraps a non-cancellation exception from fetchConfig`() =
        runTest {
            val cause = IllegalStateException("boom")
            every { client.fetchConfig(any()) } throws cause

            val thrown = assertFailsWith<AppMetricaConfigException> { provider.fetch(activate = true) }

            assertSame(cause, thrown.cause)
        }

    @Test
    fun `fetch wraps a non-cancellation exception from activateConfig`() =
        runTest {
            val cause = IllegalStateException("activate")
            val script = ClientScript(client, captureActivate = false)
            every { client.activateConfig(any()) } throws cause
            val thrown =
                awaitFailure {
                    script.fetchListener.onSuccess()
                }.withoutCoroutineRecovery()

            assertTrue(thrown is AppMetricaConfigException)
            assertSame(cause, thrown.cause)
        }

    @Test
    fun `CancellationException from activateConfig during fetch is not wrapped`() =
        runTest {
            val cancellation = CancellationException("stop")
            val script = ClientScript(client, captureActivate = false)
            every { client.activateConfig(any()) } throws cancellation
            val thrown =
                awaitFailure {
                    script.fetchListener.onSuccess()
                }.withoutCoroutineRecovery()

            assertSame(cancellation, thrown)
        }

    @Test
    fun `cancelling during fetchConfig then delivering a listener does not complete as AppMetricaConfigException`() =
        runTest {
            val script = ClientScript(client)
            val errored = launch { provider.fetch(activate = true) }
            runCurrent()
            errored.cancel()
            runCurrent()
            script.fetchListener.onError("throttled", FetchError.REQUEST_THROTTLED)
            runCurrent()
            assertCancelledNotWrapped(errored.completionCause())

            val succeeded = launch { provider.fetch(activate = false) }
            runCurrent()
            succeeded.cancel()
            runCurrent()
            script.fetchListener.onSuccess()
            runCurrent()
            assertCancelledNotWrapped(succeeded.completionCause())
        }

    @Test
    fun `cancelling after fetch success and before activate stays cancelled`() =
        runTest {
            val script = ClientScript(client)
            val job = launch { provider.fetch(activate = true) }
            runCurrent()
            script.fetchListener.onSuccess()
            runCurrent()

            assertTrue(job.isActive)
            job.cancel()
            runCurrent()
            script.activate()
            runCurrent()

            assertCancelledNotWrapped(job.completionCause())
        }

    private suspend fun TestScope.assertFetchError(
        message: String,
        error: FetchError,
    ) {
        val script = ClientScript(client)
        val thrown =
            awaitFailure {
                script.fetchListener.onError(message, error)
            }

        val failure = thrown.withoutCoroutineRecovery()
        assertTrue(failure is AppMetricaConfigException)
        assertNotNull(failure.message)
        assertTrue(failure.message!!.contains(message))
        assertTrue(failure.message!!.contains(error.name))
        assertNotNull(failure.cause)
        assertEquals(error.name, failure.cause!!.message)
        assertTrue(failure.cause is IllegalStateException)
        verify(exactly = 0) { client.activateConfig(any()) }
    }

    private suspend fun TestScope.awaitFailure(deliver: () -> Unit): Throwable {
        lateinit var thrown: Throwable
        val job =
            launch {
                try {
                    provider.fetch(activate = true)
                } catch (e: Throwable) {
                    thrown = e
                }
            }
        runCurrent()
        deliver()
        runCurrent()
        assertTrue(job.isCompleted)
        return thrown
    }

    private fun stub(
        key: String,
        raw: String,
        source: Int,
    ): VarioqubConfigValue {
        val value = VarioqubConfigValues.of(raw, source, 11L)
        every { client.getValue(key) } returns value
        return value
    }

    private suspend fun <T : Any> read(
        param: ConfigParam<T>,
        raw: String,
    ): T {
        stub(param.key, raw, varioqubSource("SOURCE_SERVER"))
        return provider.get(param).value
    }

    private fun server(raw: String): VarioqubConfigValue = VarioqubConfigValues.of(raw, varioqubSource("SOURCE_SERVER"), 0L)

    private fun varioqubSource(name: String): Int = VarioqubConfigValue::class.java.getField(name).getInt(null)

    private fun moduleDir(): File {
        val direct = File("src/main")
        if (direct.isDirectory) return File(".")
        val nested = File("providers/appmetrica")
        if (nested.resolve("src/main").isDirectory) return nested
        error("Cannot find providers/appmetrica from ${File(".").absolutePath}")
    }
}

private class ClientScript(
    client: VarioqubApi,
    captureActivate: Boolean = true,
) {
    lateinit var fetchListener: OnFetchCompleteListener
    private lateinit var activateCallback: () -> Unit

    init {
        every { client.fetchConfig(any()) } answers {
            fetchListener = firstArg()
        }
        if (captureActivate) {
            every { client.activateConfig(any()) } answers {
                activateCallback = firstArg()
            }
        }
    }

    fun activate() {
        activateCallback.invoke()
    }
}

private fun Throwable.withoutCoroutineRecovery(): Throwable {
    val nested = cause
    return if (nested != null && nested.javaClass == javaClass && nested.message == message) nested else this
}

private fun Job.completionCause(): Throwable? {
    check(isCompleted)
    var cause: Throwable? = null
    invokeOnCompletion { cause = it }
    return cause
}

private fun assertCancelledNotWrapped(completion: Throwable?) {
    assertEquals(true, completion is CancellationException)
    assertEquals(false, completion is AppMetricaConfigException)
}

private data class CustomType(
    val x: Int,
)

private enum class Theme { LIGHT, DARK }
