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

            val collected = mutableListOf<String>()

            // Collection must complete without throwing; the Flow stays alive after the
            // local provider's Flow terminates with an exception.
            configValues.observe(testParam).test {
                // Initial emission from getValue(param) — local provider's get() returns null
                // so this resolves to the default value.
                val initial = awaitItem()
                assertEquals("DEFAULT", initial.value)
                assertEquals(ConfigValue.Source.DEFAULT, initial.source)
                collected.add(initial.value)

                // Emission from the local provider's observe() before it throws.
                // distinctUntilChanged passes it because "local_value" ≠ "DEFAULT".
                val fromLocal = awaitItem()
                assertEquals("local_value", fromLocal.value)
                assertEquals(ConfigValue.Source.LOCAL, fromLocal.source)
                collected.add(fromLocal.value)

                cancelAndIgnoreRemainingEvents()
            }

            // The thrown exception must have been routed to onProviderError, not re-thrown.
            assertEquals(1, errors.size)
            assertTrue(
                errors[0] is IllegalStateException,
                "Expected IllegalStateException but was ${errors[0]::class}",
            )
            assertEquals("simulated provider error", errors[0].message)

            // Both values were collected — flow did not crash before the second emission.
            assertTrue("DEFAULT" in collected)
            assertTrue("local_value" in collected)
        }
}
