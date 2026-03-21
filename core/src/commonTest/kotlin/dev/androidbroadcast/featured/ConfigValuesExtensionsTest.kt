package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val darkModeParam = ConfigParam(key = "dark_mode", defaultValue = false)

class ConfigValuesExtensionsTest {
    @Test
    fun testObserveValue() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            provider.set(darkModeParam, true)

            configValues.observeValue(darkModeParam).test {
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testObserveValueEmitsDefault() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            // No override set — ConfigValues.observe() always emits the default first
            configValues.observeValue(darkModeParam).test {
                assertEquals(false, awaitItem()) // defaultValue from ConfigParam
                cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testAsStateFlow() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            val state = configValues.asStateFlow(darkModeParam, backgroundScope, SharingStarted.Eagerly)

            assertEquals(false, state.value) // initialValue = defaultValue

            provider.set(darkModeParam, true)
            testScheduler.runCurrent()

            assertEquals(true, state.value)
        }

    @Test
    fun testObserveValueUpdates() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)

            configValues.observeValue(darkModeParam).test {
                assertEquals(false, awaitItem()) // default emitted immediately
                provider.set(darkModeParam, true)
                assertEquals(true, awaitItem()) // reactive update
                cancelAndIgnoreRemainingEvents()
            }
        }
}
