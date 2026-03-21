package dev.androidbroadcast.featured.compose

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.InMemoryConfigValueProvider
import dev.androidbroadcast.featured.observeValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectAsStateTest {
    @Test
    fun observeValueFlowEmitsDefaultWhenNoValueSet() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            val param = ConfigParam("feature_flag", "default_value")

            val emitted = configValues.observeValue(param).first()

            assertEquals("default_value", emitted)
        }

    @Test
    fun observeValueFlowEmitsCurrentValueWhenSet() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            val param = ConfigParam("feature_flag", "default_value")

            provider.set(param, "enabled")

            val emitted = configValues.observeValue(param).first()

            assertEquals("enabled", emitted)
        }

    @Test
    fun observeValueFlowEmitsUpdatedValueAfterChange() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            val param = ConfigParam("feature_flag", "default_value")

            provider.set(param, "initial")
            val first = configValues.observeValue(param).first()
            assertEquals("initial", first)

            provider.set(param, "updated")
            val second = configValues.observeValue(param).first()
            assertEquals("updated", second)
        }
}
