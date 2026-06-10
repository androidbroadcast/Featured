package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderErrorObserveTest {
    private val testParam = ConfigParam("observe_error_key", "DEFAULT")

    /**
     * Fake local provider whose [observe] emits one value successfully,
     * then throws. All other required methods are no-ops.
     */
    private class OnceThrowingLocalProvider(
        private val emittedValue: String,
        private val error: Throwable,
    ) : LocalConfigValueProvider {
        override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = null

        override suspend fun <T : Any> set(
            param: ConfigParam<T>,
            value: T,
        ) = Unit

        override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) = Unit

        override suspend fun clear() = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
            flow {
                emit(ConfigValue(emittedValue as T, ConfigValue.Source.LOCAL))
                throw error
            }
    }

    @Test
    fun observeDoesNotPropagateWhenLocalProviderFlowThrows() =
        runTest {
            val errors = mutableListOf<Throwable>()
            val provider =
                OnceThrowingLocalProvider(
                    emittedValue = "local_value",
                    error = IllegalStateException("simulated provider error"),
                )
            val configValues =
                ConfigValues(
                    localProvider = provider,
                    onProviderError = { errors.add(it) },
                )

            // With the trigger-model, local provider emissions are used only as change
            // signals — their payloads are discarded. getValue() is called for each signal,
            // and since the local provider's get() returns null (no value stored), the
            // resolved value is DEFAULT. Because the second DEFAULT equals the first,
            // distinctUntilChanged suppresses it and no second frame is emitted.
            configValues.observe(testParam).test {
                // Initial emission — local.get() returns null, falls through to DEFAULT.
                val initial = awaitItem()
                assertEquals("DEFAULT", initial.value)
                assertEquals(ConfigValue.Source.DEFAULT, initial.source)

                // No second frame: the signal from the local provider re-resolves to DEFAULT
                // which is deduplicated. The exception is routed to onProviderError.
                cancelAndIgnoreRemainingEvents()
            }

            // The thrown exception must have been routed to onProviderError, not re-thrown.
            assertEquals(1, errors.size)
            assertTrue(
                errors[0] is IllegalStateException,
                "Expected IllegalStateException but was ${errors[0]::class}",
            )
            assertEquals("simulated provider error", errors[0].message)
        }
}
