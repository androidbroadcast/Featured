package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProviderErrorHandlingTest {
    private val testParam = ConfigParam("test_key", "default_value")

    private class ThrowingRemoteProvider(
        private val error: Throwable = RuntimeException("remote failure"),
    ) : RemoteConfigValueProvider {
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = throw error

        override suspend fun fetch(activate: Boolean) = Unit
    }

    private class ThrowingLocalProvider(
        private val error: Throwable = RuntimeException("local failure"),
        private val updates: MutableSharedFlow<ConfigValue<Any>> = MutableSharedFlow(),
    ) : LocalConfigValueProvider {
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = throw error

        override suspend fun <T : Any> set(
            param: ConfigParam<T>,
            value: T,
        ) = Unit

        override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) = Unit

        override suspend fun clear() = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> = updates as Flow<ConfigValue<T>>
    }

    // --- RED: getValue() never throws ---

    @Test
    fun getValueDoesNotThrowWhenRemoteProviderFails() =
        runTest {
            val configValues = ConfigValues(remoteProvider = ThrowingRemoteProvider())
            val result = configValues.getValue(testParam)
            assertEquals("default_value", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun getValueDoesNotThrowWhenLocalProviderFails() =
        runTest {
            val configValues = ConfigValues(localProvider = ThrowingLocalProvider())
            val result = configValues.getValue(testParam)
            assertEquals("default_value", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun getValueFallsBackToRemoteWhenLocalProviderFails() =
        runTest {
            val remoteProvider =
                object : RemoteConfigValueProvider {
                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T> =
                        @Suppress("UNCHECKED_CAST")
                        ConfigValue("remote_value" as T, ConfigValue.Source.REMOTE)

                    override suspend fun fetch(activate: Boolean) = Unit
                }
            val configValues =
                ConfigValues(
                    localProvider = ThrowingLocalProvider(),
                    remoteProvider = remoteProvider,
                )
            val result = configValues.getValue(testParam)
            assertEquals("remote_value", result.value)
            assertEquals(ConfigValue.Source.REMOTE, result.source)
        }

    // --- RED: onProviderError callback ---

    @Test
    fun onProviderErrorCallbackInvokedWhenRemoteProviderFails() =
        runTest {
            val capturedErrors = mutableListOf<Throwable>()
            val configValues =
                ConfigValues(
                    remoteProvider = ThrowingRemoteProvider(),
                    onProviderError = { capturedErrors.add(it) },
                )
            configValues.getValue(testParam)
            assertEquals(1, capturedErrors.size)
            assertEquals("remote failure", capturedErrors[0].message)
        }

    @Test
    fun onProviderErrorCallbackInvokedWhenLocalProviderFails() =
        runTest {
            val capturedErrors = mutableListOf<Throwable>()
            val configValues =
                ConfigValues(
                    localProvider = ThrowingLocalProvider(),
                    onProviderError = { capturedErrors.add(it) },
                )
            configValues.getValue(testParam)
            assertEquals(1, capturedErrors.size)
        }

    @Test
    fun onProviderErrorNotInvokedWhenProvidersSucceed() =
        runTest {
            var errorCallbackInvoked = false
            val configValues =
                ConfigValues(
                    localProvider = InMemoryConfigValueProvider(),
                    onProviderError = { errorCallbackInvoked = true },
                )
            configValues.getValue(testParam)
            assertEquals(false, errorCallbackInvoked)
        }

    // --- default handler (logProviderError) does not throw ---

    @Test
    fun defaultHandlerDoesNotThrowWhenRemoteProviderFailsDuringObserve() =
        runTest {
            // ConfigValues constructed without an explicit onProviderError — the default
            // platform-log handler must not propagate the exception to the caller.
            val configValues = ConfigValues(remoteProvider = ThrowingRemoteProvider())
            configValues.observe(testParam).test {
                val emission = awaitItem()
                assertEquals("default_value", emission.value)
                assertEquals(ConfigValue.Source.DEFAULT, emission.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- RED: observe() does not terminate on provider error ---

    @Test
    fun observeDoesNotTerminateWhenGetValueThrowsDuringInitialEmit() =
        runTest {
            // After first failure, remote recovers
            var callCount = 0
            val remoteProvider =
                object : RemoteConfigValueProvider {
                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
                        callCount++
                        if (callCount == 1) throw RuntimeException("transient error")
                        @Suppress("UNCHECKED_CAST")
                        return ConfigValue("recovered" as T, ConfigValue.Source.REMOTE)
                    }

                    override suspend fun fetch(activate: Boolean) = Unit
                }
            val configValues = ConfigValues(remoteProvider = remoteProvider)

            configValues.observe(testParam).test {
                // First call throws → should emit default, not crash
                val first = awaitItem()
                assertEquals("default_value", first.value)

                // After fetch, recovered value is emitted
                configValues.fetch()
                val second = awaitItem()
                assertEquals("recovered", second.value)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeErrorCallbackInvokedAndFlowContinuesAfterProviderFailure() =
        runTest {
            val capturedErrors = mutableListOf<Throwable>()
            val configValues =
                ConfigValues(
                    remoteProvider = ThrowingRemoteProvider(),
                    onProviderError = { capturedErrors.add(it) },
                )

            configValues.observe(testParam).test {
                val emission = awaitItem()
                assertEquals("default_value", emission.value)
                assertEquals(1, capturedErrors.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- default handler (logProviderError) does not throw on getValue ---

    @Test
    fun defaultHandlerDoesNotThrowWhenRemoteProviderFailsDuringGetValue() =
        runTest {
            // ConfigValues without explicit onProviderError — the default platform-log handler
            // must not propagate the exception out of getValue.
            val configValues = ConfigValues(remoteProvider = ThrowingRemoteProvider())
            val result = configValues.getValue(testParam)
            assertEquals("default_value", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    // --- throwing custom handler does not propagate through getValue or observe ---

    @Test
    fun throwingHandlerDoesNotPropagateOutOfGetValue() =
        runTest {
            val configValues =
                ConfigValues(
                    remoteProvider = ThrowingRemoteProvider(),
                    onProviderError = { throw IllegalStateException("handler bug") },
                )
            // The handler throws but getValue must still return the default and not propagate.
            val result = configValues.getValue(testParam)
            assertEquals("default_value", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun throwingHandlerDoesNotTerminateObserveFlow() =
        runTest {
            val configValues =
                ConfigValues(
                    remoteProvider = ThrowingRemoteProvider(),
                    onProviderError = { throw IllegalStateException("handler bug") },
                )
            configValues.observe(testParam).test {
                val emission = awaitItem()
                // Flow emits default and does not crash despite the handler throwing.
                assertEquals("default_value", emission.value)
                assertEquals(ConfigValue.Source.DEFAULT, emission.source)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- CancellationException propagates and is not reported as a provider error ---

    @Test
    fun cancellationExceptionPropagatesOutOfGetValue() =
        runTest {
            val errorInvoked = mutableListOf<Throwable>()
            val configValues =
                ConfigValues(
                    remoteProvider = ThrowingRemoteProvider(error = CancellationException("cancelled")),
                    onProviderError = { errorInvoked.add(it) },
                )
            assertFailsWith<CancellationException> {
                configValues.getValue(testParam)
            }
            // The handler must NOT have been called — CancellationException is not a provider error.
            assertFalse(errorInvoked.isNotEmpty(), "onProviderError must not be invoked for CancellationException")
        }
}
