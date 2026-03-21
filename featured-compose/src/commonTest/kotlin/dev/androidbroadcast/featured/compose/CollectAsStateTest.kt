package dev.androidbroadcast.featured.compose

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.InMemoryConfigValueProvider
import dev.androidbroadcast.featured.observeValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectAsStateTest {
    private val provider = InMemoryConfigValueProvider()
    private val configValues = ConfigValues(localProvider = provider)
    private val param = ConfigParam("feature_flag", "default_value")

    @Test
    fun observeValueFlowEmitsDefaultWhenNoValueSet() =
        runTest {
            val emitted = configValues.observeValue(param).first()

            assertEquals("default_value", emitted)
        }

    @Test
    fun observeValueFlowEmitsCurrentValueWhenSet() =
        runTest {
            provider.set(param, "enabled")

            val emitted = configValues.observeValue(param).first()

            assertEquals("enabled", emitted)
        }

    @Test
    fun observeValueFlowEmitsUpdatedValueToActiveSubscriber() =
        runTest {
            provider.set(param, "initial")

            configValues.observeValue(param).test {
                assertEquals("initial", awaitItem())

                provider.set(param, "updated")
                assertEquals("updated", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
