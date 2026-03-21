package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildDebugItemsTest {
    private fun fakeConfigValues(
        source: ConfigValue.Source,
        value: Any,
    ): ConfigValues {
        val provider =
            object : LocalConfigValueProvider {
                @Suppress("UNCHECKED_CAST")
                override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T> = ConfigValue(value = value as T, source = source)

                override suspend fun <T : Any> set(
                    param: ConfigParam<T>,
                    value: T,
                ) = Unit

                override suspend fun <T : Any> resetOverride(param: ConfigParam<T>) = Unit

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> =
                    flowOf(ConfigValue(value = value as T, source = source))
            }
        return ConfigValues(localProvider = provider)
    }

    @Test
    fun buildDebugItems_setsSourceFromConfigValue() =
        runTest {
            val param = ConfigParam(key = "p", defaultValue = true)
            val configValues = fakeConfigValues(ConfigValue.Source.REMOTE, true)

            val items = buildDebugItems(configValues, listOf(param))

            assertEquals(1, items.size)
            assertEquals(ConfigValue.Source.REMOTE, items[0].source)
        }

    @Test
    fun buildDebugItems_setsOverrideValueWhenSourceIsLocal() =
        runTest {
            val param = ConfigParam(key = "p2", defaultValue = false)
            val configValues = fakeConfigValues(ConfigValue.Source.LOCAL, true)

            val items = buildDebugItems(configValues, listOf(param))

            assertEquals(true, items[0].overrideValue)
        }

    @Test
    fun buildDebugItems_overrideValueNullWhenSourceIsDefault() =
        runTest {
            val param = ConfigParam(key = "p3", defaultValue = "hello")
            val configValues = fakeConfigValues(ConfigValue.Source.DEFAULT, "hello")

            val items = buildDebugItems(configValues, listOf(param))

            assertNull(items[0].overrideValue)
        }

    @Test
    fun buildDebugItems_returnsItemForEachParam() =
        runTest {
            val params =
                listOf(
                    ConfigParam(key = "a", defaultValue = 1),
                    ConfigParam(key = "b", defaultValue = 2),
                    ConfigParam(key = "c", defaultValue = 3),
                )
            val configValues = fakeConfigValues(ConfigValue.Source.DEFAULT, 1)

            val items = buildDebugItems(configValues, params)

            assertEquals(3, items.size)
        }
}
